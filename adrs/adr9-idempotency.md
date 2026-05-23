# ADR 9: Idempotentie en Dubbele Berichten Preventie

**Status:** Voorgesteld

**Datum:** 23 mei 2026

**Besluitvormer:** Architectuurteam

## Context

In een gedistribueerde SaaS-omgeving kunnen netwerkfouten ertoe leiden dat de OpenMRS-plugin een succesvol verwerkt bericht nogmaals verstuurt. Zonder idempotentiemechanisme zou onze module duplicate records aanmaken. Dit leidt tot datavervuiling, database-overhead en in het ergste geval het dubbel verzenden van herinneringen naar patiënten, wat de patiëntervaring schaadt.

## Besluit

Wij implementeren een gelaagde idempotentiestrategie:
1. **Op API-niveau:** Validatie op basis van een samengestelde functionele uniekheid (`patientId` + `scheduledTime`).
2. **Op Scheduler- en Worker-niveau:** Validatie op basis van strikte, atomaire status- en notificatietransities.

## Argumentatie

### 1. Inkomende API Idempotentie (In-flight preventie)
Normaal gesproken wordt idempotentie afgedwongen via een uniek `appointment_id`. Echter, omdat onze database (`Appointments` collectie) zelf de unieke ObjectIDs genereert bij een `save()`, zou het twee keer insturen van exact dezelfde payload resulteren in twee verschillende documenten met elk een eigen ID. 

Om dit op te lossen, definiëren we een **samengestelde functionele uniekheid** op de combinatie van:
* `patientId`
* `scheduledTime`

**Werking:** Voordat de API-controller een nieuw document opslaat, voert hij een snelle check uit in MongoDB: `existsByPatientIdAndScheduledTime(...)`. Als deze combinatie al bestaat, weigert de API het duplicate document en retourneert een `200 OK` of `409 Conflict`, zonder een nieuw record aan te maken. 

### 2. Systeem Interne Idempotentie (Scheduler & Worker)
Voor de interne processen vertrouwen we op de status-tags uit **ADR 2** en de notificatie-timeline uit **ADR 7**:

* **De Scheduler:** Vóórdat een afspraak in RabbitMQ wordt geplaatst, controleert de Scheduler of de specifieke notificatiestatus (`reminder24h` of `reminder1h`) exact gelijk is aan `SCHEDULED`. Door middel van een atomaire database-update (`findOneAndUpdate`) wordt de status direct veranderd naar `QUEUED`. Een eventuele tweede instance van de Scheduler vist hierdoor direct achter het net.
* **De Worker:** Vlak voordat de Worker de externe API (bijv. SwiftSend) aanroept, raadpleegt hij nogmaals de database. Als de status in de tussentijd is veranderd naar `SENT`, `FAILED` of `CANCELLED`, breekt de Worker de executie direct af.
