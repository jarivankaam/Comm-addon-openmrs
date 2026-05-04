# ADR 5: Inrichting van de Queuing Infrastructuur (RabbitMQ)

**Status:** Voorgesteld  
**Datum:** 4 mei 2026  
**Besluitvormer:** Architectuurteam

## Context
De communicatiemodule moet berichten versturen via externe providers (zoals SwiftSend en LegacyLink). Deze providers kunnen last hebben van downtime of snelheidsbeperkingen (rate limiting). Omdat we te maken hebben met kritieke medische informatie (bijv. afspraakherinneringen), mogen berichten niet verloren gaan als een provider tijdelijk onbereikbaar is. Daarnaast moet het systeem grote pieken in het aantal berichten kunnen opvangen zonder dat de HL7 FHIR API onze module blokkeert.

## Besluit
Wij kiezen voor **RabbitMQ** als message broker, waarbij we gebruikmaken van het **Competing Consumers** patroon en een specifieke **Dead Letter Exchange (DLX)** strategie voor foutafhandeling.

De inrichting ziet er als volgt uit:

| Onderdeel | Implementatie |
| :--- | :--- |
| **Exchange Type** | `Direct Exchange` (berichten worden gerouteerd op basis van provider-ID). |
| **Queues** | Eén dedicated queue per provider (bijv. `q.sms.swiftsend`, `q.whatsapp.asyncflow`). |
| **Berichten** | Persistent (opgeslagen in database) om dataverlies bij herstart te voorkomen. |
| **Retry-mechanisme** | Exponential backoff (pauzes tussen pogingen worden steeds langer). |
| **Foutafhandeling** | Dead Letter Queues (DLQ) voor berichten die na 3 pogingen falen. |

## Argumentatie

### 1. Ontkoppeling en Load Leveling
Door RabbitMQ tussen de FHIR-ontvanger en de verzend-logica te plaatsen, creëren we een buffer. Als OpenMRS in één seconde 500 afspraken doorstuurt, worden deze veilig in de wachtrij geplaatst. Onze "workers" verwerken deze berichten op een tempo dat de externe providers aankunnen, zonder dat de hoofdapplicatie vertraagt.

### 2. Betrouwbaarheid via Retries & Dead Letter Queues
Niet elke fout is fataal. Als LegacyLink een `503 Service Unavailable` geeft, probeert de worker het na een korte pauze opnieuw.
* **Retry:** Berichten die tijdelijk falen, worden teruggeplaatst in de wachtrij.
* **DLQ:** Als een bericht na 3 pogingen nog steeds faalt (bijv. door een ongeldig telefoonnummer), verhuist het naar de `q.failed.messages`. Dit voorkomt "poison messages" die de rest van de wachtrij blokkeren, terwijl beheerders de fout handmatig kunnen onderzoeken in het dashboard.

### 3. Granulaire Schaalbaarheid
Omdat we per provider een aparte wachtrij hebben, kunnen we specifiek opschalen. Als we merken dat de `SwiftSend` wachtrij volloopt, kunnen we extra workers (containers) opstarten die alleen naar die specifieke wachtrij luisteren, zonder extra resources te verspillen aan de andere providers.

### 4. Security & Compliance
RabbitMQ ondersteunt encryptie in transit via TLS. Berichten in de wachtrij bevatten bovendien alleen de noodzakelijke FHIR-data. Volgens de 14-dagen eis in ADR 2, worden berichten die succesvol zijn afgehandeld direct uit de wachtrij verwijderd.

## Overwogen alternatieven

### Directe API-aanroepen *(Rejected)*
Hierbij zou de Java-applicatie direct de API van SwiftSend aanroepen zodra het FHIR-bericht binnenkomt.
* **Waarom niet?** Als de provider traag reageert, blijft de FHIR-verbinding met OpenMRS openstaan. Bij downtime van de provider gaat het bericht direct verloren ("fire and forget").

### Redis Pub/Sub *(Rejected)*
* **Waarom niet?** Redis is fantastisch voor snelheid, maar standaard minder sterk voor gegarandeerde aflevering van berichten als de server crasht. RabbitMQ biedt betere garanties voor "at-least-once delivery".

## Consequenties
* **Monitoring:** We moeten de "Queue Depth" (hoeveel berichten staan er nog?) monitoren via de OpenTelemetry stack (Prometheus/Grafana) om tijdig vertragingen te detecteren.
* **Complexiteit:** Het beheer van de RabbitMQ broker (users, vhosts, permissions) voegt een extra laag toe aan de infrastructuur.
* **Idempotentie:** Onze verzend-logica moet "idempotent" zijn. In het zeldzame geval dat een bericht dubbel uit de wachtrij komt, moet de module herkennen dat dit bericht al verstuurd is om dubbele SMS'jes naar patiënten te voorkomen.
