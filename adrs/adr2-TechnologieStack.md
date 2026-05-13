# ADR 2: Technologie Stack

**Status:** Voorgesteld

**Datum:** 23 april 2026

**Besluitvormer:** Architectuurteam

## Context

De communicatiemodule moet als betrouwbare SaaS-oplossing functioneren en voldoen aan specifieke timing-eisen (notificaties exact 24 uur en 1 uur voor een afspraak). Dit vereist een architectuur die niet alleen berichten kan ontvangen en versturen, maar ook taken kan inplannen, annuleringen kan verwerken en schaalbaar is onder zware belasting.

## Besluit

Wij hebben gekozen voor de volgende technologie stack:

| Component                        | Keuze                            |
|----------------------------------|----------------------------------|
| Taal                             | Java 8                           |
| Framework                        | Spring (Boot) 2.7.x                |
| API Applicatie                    | Spring Web (Ingress van FHIR Resources) |
| Berichtenwachtrij (Message Broker) | RabbitMQ                        |
| Scheduler                        | Spring Boot @Scheduled (Polling van DB)  |
| Worker applicatie               | Spring AMQP (Message verwerking & verzending |
| Database                | MongoDB met Compound & TTL Indexen|
| Monitoring                       | OpenTelemetry met Prometheus & Grafana |
| FHIR Bibliotheek                 | HAPI FHIR                        |

## Argumentatie

### 1. Taal & Framework: Java 8 met Spring Boot 2.7

**Waarom:** Hoewel Java 8 ouder is, is het extreem stabiel en wordt het breed ondersteund binnen de OpenMRS-community. Spring Boot 2.7 is de laatste grote versie die nog Java 8 ondersteunt.

**Match met eisen:** Spring Boot heeft een volwassen ecosysteem voor beveiliging (Spring Security) en integraties, wat essentieel is voor de gevraagde AES-256 encryptie en TLS 1.3 verbindingen.

**Capaciteiten team:** Java is een "type-safe" taal, wat cruciaal is bij het verwerken van medische data om runtime-fouten te voorkomen.

### 2. Berichtenwachtrij: RabbitMQ

**Waarom:** De opdracht eist een "zelfontworpen fallback- of retrymechanisme". RabbitMQ is perfect voor het tijdelijk opslaan van berichten als een provider (zoals LegacyLink) een storing heeft.

**Betrouwbaarheid:** Het ondersteunt Dead Letter Queues (DLQ). Als een bericht na 3 pogingen nog niet is verzonden, wordt het veilig geparkeerd voor handmatige inspectie, zonder dat het systeem blokkeert.

**Persistentie:** Berichten worden op schijf opgeslagen, zodat ze niet verloren gaan bij een herstart van de broker.

### 3. De Worker Applicatie (Execution & Validation)
De Worker is een onafhankelijke service die verantwoordelijk is voor de uiteindelijke aflevering. Door de Worker als een losstaande service te draaien, kunnen we de rekenkracht die nodig is voor zware encryptie en externe API-calls onafhankelijk schalen zonder de API-ontvangst te hinderen.

De kracht van de Worker zit in de "Just-in-Time" validatie:

**Status Check:** Voordat de Worker een bericht naar een provider (bijv. SwiftSend) stuurt, raadpleegt hij de database om te controleren of de afspraak nog de status SCHEDULED heeft.

**Annuleringen:** Als een patiënt in de tussentijd heeft geannuleerd, is de status in MongoDB gewijzigd naar CANCELLED. De Worker ziet dit, breekt de verzending af en logt dit resultaat.

**Tijd Check:** De Worker controleert of de afspraak niet al begonnen is (eis 1.3 van de opdracht).

### 4. Database: MongoDB
**Waarom:** FHIR-resources zijn in de kern gestructureerde datastructuren (JSON). MongoDB, als document-oriented database, kan deze resources opslaan zonder ze te hoeven forceren in een streng tabelstructuur.

**Data Retention:** MongoDB biedt TTL (Time-To-Live) indexen. Hiermee kunnen we technisch garanderen dat patiëntgegevens na exact 14 dagen worden verwijderd, simpelweg door een verlooptijd op het document in te stellen.

**Capaciteiten team:** Het team heeft ruime ervaring met NoSQL/MongoDB en geen ervaring met PostgreSQL. Het kiezen voor MongoDB voorkomt kostbare leercurves en implementeer fouten tijdens de implementatie.

**Status-gebaseerd beheer:** We slaan afspraken op met status-tags (SCHEDULED, QUEUED, SENT, CANCELLED). Dit stelt ons in staat om annuleringen simpelweg te verwerken door een status-update, zonder complexe operaties in de berichtenwachtrij.

**Performance:** Door gebruik van Compound Indexen op status en scheduledTime kan de scheduler miljoenen records scannen in milliseconden zonder de database te overbelasten.

**Data Splitsing:** Er moet een duidelijk onderscheid gemaakt worden in de database tussen het 'Appointment' document (Verwijderen na 14 dagen) en het 'AuditLog' document (metadata voor facturatie, bewaren voor 1 jaar).

### 5. Monitoring: OpenTelemetry (OTEL)
**Waarom:** In een SaaS-omgeving met meerdere providers is het cruciaal om te weten waar een vertraging optreedt. OpenTelemetry biedt Distributed Tracing. Hiermee kunnen we een bericht volgen vanaf de binnenkomst vanuit OpenMRS, door de RabbitMQ-wachtrij, tot aan de API van de messaging provider.

**Vendor Neutrality:** OTEL zorgt ervoor dat onze monitoring niet vastzit aan één specifieke tool. We kunnen de data naar Prometheus/Grafana sturen, maar in de toekomst ook eenvoudig overstappen naar andere professionele dashboards zonder de code aan te passen.

### 6. FHIR Bibliotheek: HAPI FHIR

**Waarom:** Dit is de wereldstandaard voor Java-applicaties die met HL7 FHIR werken.

**Match met eisen:** Het regelt de validatie, parsing en syntaxis-controle die vereist is voor HL7-systemen, zodat we dit niet zelf vanaf nul hoeven te bouwen.

### 7. Scheduler: Spring Boot @Scheduled

**Waarom:** De scheduler fungeert als de "wekker" van het systeem. Hij ontkoppelt de ontvangst van de afspraak (API) van het versturen (Worker). Dit is essentieel voor de gevraagde 24h/1h notificaties.

**Mechanisme:** De scheduler haalt in batches afspraken op die klaarstaan voor verzending en plaatst enkel een referentie (ID) in RabbitMQ. Dit verhoogt de veiligheid en garandeert dat de Worker altijd de meest actuele data uit de database ophaalt. Dit voorkomt ook dat de queue volstroomt met berichten die pas over uren verwerkt hoeven te worden.

## Overwogen alternatieven

**NestJS** *(Rejected)*: Dit is een TypeScript framework. Omdat wij als team Java hebben gekozen als basistaal, is NestJS technisch niet compatibel.

**Apache Kafka** *(Rejected)*: Hoewel zeer krachtig, is Kafka vaak te complex voor een communicatiemodule van deze omvang. RabbitMQ is lichter en makkelijker te beheren voor een SaaS-start.

**In-memory opslag** *(Rejected)*: We hebben een echte database nodig omdat we meta-informatie tot een jaar moeten bewaren voor de facturatiecontrole.

**PostgreSQL** *(Rejected)*: Hoewel krachtig voor relationele data, ontbreekt het team aan de nodige expertise om dit veilig en efficiënt in te richten binnen de tijdlijn van het project.

## Consequenties

**Ontwikkeling:** Het team moet kennis hebben van Spring Boot en Dependency Injection.

**Consistentie:** De Worker moet altijd een leesactie op de database uitvoeren vóór verzending. Dit verhoogt de betrouwbaarheid maar zorgt voor een kleine extra belasting op MongoDB.

**Idempotentie:** Onze verzend-logica moet herkennen of een bericht per ongeluk dubbel wordt aangeboden om dubbele SMS'jes te voorkomen.

**Beheer:** Er moet een RabbitMQ-server en MongoDB-server worden ingericht en onderhouden (naast de applicatie zelf).

**Beveiliging:** Omdat we Java 8 gebruiken, moeten we extra goed letten op het up-to-date houden van dependencies om beveiligingslekken te voorkomen.
