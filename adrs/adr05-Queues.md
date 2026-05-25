# ADR 5: Inrichting van de Queuing Infrastructuur (RabbitMQ)

**Status:** Geaccepteerd

**Datum:** 25 mei 2026

**Besluitvormer:** Architectuurteam

## Context
Onze communicatiemodule moet op exact gezette tijden (24 uur en 1 uur van tevoren) herinneringen versturen. Wanneer de Scheduler op specifieke piekmomenten duizenden afspraken tegelijkertijd identificeert die verwerkt moeten worden, mag dit de database en de applicatie-performance niet platleggen. We hebben een asynchrone buffer nodig om deze pieken op te vangen en de berichten gecontroleerd en betrouwbaar over te dragen aan de workers.

## Besluit
Wij kiezen voor **één centrale RabbitMQ wachtrij** die exclusief gepositioneerd is **tussen de Scheduler en de Worker-applicatie**. We maken hierbij gebruik van een `Fanout` of `Direct` Exchange setup met het **Competing Consumers** patroon.

De inrichting ziet er als volgt uit:

| Onderdeel | Implementatie |
| :--- | :--- |
| **Architectonische Positie** | Tussen de `Scheduler` (Producer) en de `Worker` (Consumer). |
| **Aantal Queues** | Één centrale, persistente wachtrij (`q.appointments.process`). |
| **Payload** | Minimale data (enkel het `appointment_id` en het notificatietype: 24h of 1h). |
| **Afgifte-garantie** | At-least-once delivery middels handmatige Message Acknowledgements (ACKs). |
| **Foutafhandeling** | Dead Letter Queue (`q.appointments.failed`) na 3 mislukte pogingen. |

## Argumentatie

### 1. Waarom één centrale queue tussen Scheduler en Worker?
In plaats van de queue direct achter de API te zetten en te splitsen per provider, kiezen we voor een gecentraliseerde, interne verwerkingsqueue. 
* **Database Ontlasting (Load Leveling):** De Scheduler selecteert in bulk de afspraken die nu verstuurd moeten worden en 'dumpt' de ID's direct in RabbitMQ. De Scheduler is daarna meteen klaar. De queue fungeert als een stuwdam die de piek opvangt.
* **Dunne Payload (Privacy-by-Design):** Omdat de queue tussen onze eigen interne componenten zit, sturen we geen privacygevoelige patiëntdata over de message broker. We sturen enkel het `appointment_id`. Pas wanneer een Worker het ID uit de queue pakt, haalt hij "Just-in-Time" de meest actuele, versleutelde data op uit MongoDB.

### 2. Just-in-Time Validatie & Flexibiliteit
Omdat de routering naar de specifieke messaging provider (SwiftSend, SecurePost, etc.) pas *binnenin* de Worker plaatsvindt (middels het Adapter Pattern uit **ADR 2**), hoeft RabbitMQ niks te weten over externe partijen. Mocht een patiënt 5 minuten geleden hebben geannuleerd, dan ziet de Worker dat direct bij de statuscheck in de database zodra hij het ID uit de queue pakt. Het bericht wordt dan simpelweg niet verstuurd. Dit was onmogelijk geweest als het bericht al volledig opgemaakt in een provider-specifieke queue had gestaan.

### 3. Betrouwbaarheid via Handmatige ACKs (Fault Tolerance)
We maken gebruik van handmatige acknowledgements (`manual ACK`). 
* Wanneer een Worker een ID uit de wachtrij pakt, blijft het bericht gereserveerd in RabbitMQ. 
* Pas als de Worker de externe API-call succesvol heeft afgerond en de status in MongoDB op `SENT` heeft gezet, stuurt de Worker een `ACK` naar RabbitMQ en verdwijnt het bericht.
* **Crash-fallback:** Mocht de Worker-container halverwege crashen of de verbinding verliezen, dan merkt RabbitMQ dat de TCP-connectie wegvalt. Het ID wordt onmiddellijk weer bovenaan de queue gezet (`requeue`) en door een andere actieve Worker opgepakt. Er gaat dus nooit een notificatie verloren.

### 4. Horizontale Schaalbaarheid van Consumers
Het Competing Consumers patroon stelt ons in staat om de verwerkingscapaciteit lineair op te schalen. Als we merken dat de centrale wachtrij te diep wordt tijdens piekmomenten, kunnen we eenvoudig extra instanties van de Worker-container opstarten. RabbitMQ verdeelt de ID's automatisch via round-robin over alle beschikbare workers.

## Overwogen alternatieven

### Dedicated Queues per Externe Provider *(Rejected)*
Hierbij zou RabbitMQ de berichten al direct moeten sorteren in aparte wachtrijen voor SwiftSend, LegacyLink, etc.
* **Waarom niet?** Dit maakt de architectuur erg rigide. Als een ziekenhuis besluit te wisselen van provider, of
