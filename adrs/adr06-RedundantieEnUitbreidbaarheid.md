# ADR 6: Redundantie en Uitbreidbaarheid

**Status:** Geaccepteerd

**Datum:** 18 mei 2026

**Besluitvormer:** Architectuurteam

## Context

Als SaaS-platform voor wereldwijde OpenMRS-klinieken mag de communicatiemodule geen *Single Point of Failure* bevatten. Downtime betekent dat patiënten cruciale afspraakinformatie missen. 
Daarnaast eist de opdrachtgever dat het systeem toekomstbestendig is: 
nieuwe messaging providers en nieuwe functionele OpenMRS-modules (zoals testresultaten) moeten eenvoudig geïntegreerd kunnen worden zonder ingrijpende architectuurwijzigingen.

## Besluit

Wij kiezen voor een **horizontaal schaalbare micro-architectuur** waarbij redundantie per component wordt ingericht en uitbreidbaarheid wordt geborgd via het **Strategy/Adapter Design Pattern** in de Worker.

## Argumentatie

### 1. Redundantie & High Availability (HA)

Door de fysieke scheiding van componenten in ons C4-model kunnen we redundantie als volgt inrichten:

* **Stateless Componenten (API & Workers):** Er worden minimaal twee instanties van de API (achter een load balancer) en twee instanties van de Worker gedraaid. De Workers consumeren uit de wachtrij via het *Competing Consumers* principe; als één Worker crasht, herverdeelt RabbitMQ de openstaande taken direct naar actieve instanties.
* **Stateful Componenten (DB & Queue):** MongoDB wordt uitgerold als een *Replica Set* (Primary-Secondary) en RabbitMQ maakt gebruik van gecentreerde *Quorum Queues*. Dit garandeert dat data en berichten overleven wanneer een fysieke server uitvalt.
* **Scheduler Concurrency Control:** Om te voorkomen dat redundante Schedulers dezelfde notificatie dubbel inplannen, dwingen we databasedistributie af middels atomaire status-updates (`SCHEDULED` → `QUEUED`). Alleen de Scheduler die de update succesvol uitvoert, mag de taak aanbieden aan de queue.

### 2. Uitbreidbaarheid (Extensibility)

* **OpenMRS Modules (Eis 12):** De API en MongoDB maken gebruik van het flexibele HL7 FHIR-formaat. Als er in de toekomst een module voor "Medische testresultaten" (bijv. `Observation` of `DiagnosticReport` resources) wordt toegevoegd, kan deze via dezelfde API-ingang en database-architectuur stromen zonder dat de infrastructuur veranderd moet worden.
* **Messaging Providers (Eis 3):** Binnen de Worker applicatie wordt een abstracte `MessagingProvider` interface gedefinieerd. Het toevoegen van een nieuwe provider (bijv. SwiftSend of een toekomstige provider) vereist enkel het schrijven van een nieuwe concrete adapterklasse. De API, Database en Scheduler blijven volledig ongewijzigd.

## Overwogen alternatieven

### Alles-in-één Monoliet *(Rejected)*

**Waarom niet:** Als de verzendlogica (Worker) en de planning (Scheduler) in dezelfde applicatieomgeving zitten, is het onmogelijk om de Worker redundant uit te voeren zonder dat de Schedulers met elkaar in conflict raken. Daarnaast zorgt een crash in één provider-koppeling er dan voor dat het hele systeem (inclusief de API-ontvangst) platligt.

## Consequenties

* **Complexiteit in Deployment:** Onze Docker-omgeving (Docker Compose of Kubernetes) moet correct geconfigureerd worden om meerdere replica's van de API's en Workers te starten en te beheren.
* **Distributed Locking overhead:** Er is extra code/logica nodig in de Schedulers om te zorgen dat ze netjes om de beurt de database controleren, wat een minimale overhead met zich meebrengt.
* **Strikte Interfaces:** Ontwikkelaars die in de toekomst nieuwe providers toevoegen, moeten zich strikt houden aan de gedefinieerde Java-interfaces in de Worker.
