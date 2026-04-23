# ADR 2: Technologie Stack

**Status:** Voorgesteld

**Datum:** 23 april 2026

**Besluitvormer:** Architectuurteam

## Context

De communicatiemodule moet als een betrouwbare, schaalbare SaaS-oplossing functioneren. Het moet zware encryptie (AES-256) ondersteunen, HL7 FHIR-standaarden volgen en robuuste wachtrij-mechanismen hebben voor het geval dat messaging providers (zoals SwiftSend) offline gaan.

## Besluit

Wij hebben gekozen voor de volgende technologie stack:

| Component                        | Keuze                            |
|----------------------------------|----------------------------------|
| Taal                             | Java 8                           |
| Framework                        | Spring (Boot) 2.7.x                |
| Berichtenwachtrij (Message Broker) | RabbitMQ                        |
| Database (Opslag)                | MongoDB                      |
| Monitoring                       | OpenTelemetry met Prometheus & Grafana |
| FHIR Bibliotheek                 | HAPI FHIR                        |

## Argumentatie

### 1. Taal & Framework: Java 8 met Spring Boot 2.7

**Waarom:** Hoewel Java 8 ouder is, is het extreem stabiel en wordt het breed ondersteund binnen de OpenMRS-community. Spring Boot 2.7 is de laatste grote versie die nog Java 8 ondersteunt.

**Match met eisen:** Spring Boot heeft een volwassen ecosysteem voor beveiliging (Spring Security) en integraties, wat essentieel is voor de gevraagde AES-256 encryptie en TLS 1.3 verbindingen.

**Capaciteiten team:** Java is een "type-safe" taal, wat cruciaal is bij het verwerken van medische data om runtime-fouten te voorkomen.

### 2. Berichtenwachtrij: RabbitMQ

**Waarom:** De opdracht eist een "zelfontworpen fallback- of retrymechanisme". RabbitMQ is perfect voor het tijdelijk opslaan van berichten als een provider (zoals LegacyLink) een storing heeft.

**Match met eisen:** Het ondersteunt Dead Letter Queues (DLQ). Als een bericht na 3 pogingen nog niet is verzonden, wordt het veilig geparkeerd voor handmatige inspectie, zonder dat het systeem blokkeert.

### 3. Database: MongoDB
**Waarom:** FHIR-resources zijn in de kern gestructureerde datastructuren (JSON). MongoDB, als document-oriented database, kan deze resources opslaan zonder ze te hoeven forceren in een streng tabelstructuur.

**Data Retention:** MongoDB biedt TTL (Time-To-Live) indexen. Hiermee kunnen we technisch garanderen dat patiëntgegevens na exact 14 dagen worden verwijderd, simpelweg door een verlooptijd op het document in te stellen.

**Capaciteiten team:** Het team heeft ruime ervaring met NoSQL/MongoDB en geen ervaring met PostgreSQL. Het kiezen voor MongoDB voorkomt kostbare leercurves en implementeer fouten tijdens de implementatie.

### 4. Monitoring: OpenTelemetry (OTEL)
**Waarom:** In een SaaS-omgeving met meerdere providers is het cruciaal om te weten waar een vertraging optreedt. OpenTelemetry biedt Distributed Tracing. Hiermee kunnen we een bericht volgen vanaf de binnenkomst vanuit OpenMRS, door de RabbitMQ-wachtrij, tot aan de API van de messaging provider.

**Vendor Neutrality:** OTEL zorgt ervoor dat onze monitoring niet vastzit aan één specifieke tool. We kunnen de data naar Prometheus/Grafana sturen, maar in de toekomst ook eenvoudig overstappen naar andere professionele dashboards zonder de code aan te passen.

### 5. FHIR Bibliotheek: HAPI FHIR

**Waarom:** Dit is de wereldstandaard voor Java-applicaties die met HL7 FHIR werken.

**Match met eisen:** Het regelt de validatie, parsing en syntaxis-controle die vereist is voor HL7-systemen, zodat we dit niet zelf vanaf nul hoeven te bouwen.

## Overwogen alternatieven

**NestJS** *(Rejected)*: Dit is een TypeScript framework. Omdat wij als team Java hebben gekozen als basistaal, is NestJS technisch niet compatibel.

**Apache Kafka** *(Rejected)*: Hoewel zeer krachtig, is Kafka vaak te complex voor een communicatiemodule van deze omvang. RabbitMQ is lichter en makkelijker te beheren voor een SaaS-start.

**In-memory opslag** *(Rejected)*: We hebben een echte database nodig omdat we meta-informatie tot een jaar moeten bewaren voor de facturatiecontrole.

**PostgreSQL** *(Rejected)*: Hoewel krachtig voor relationele data, ontbreekt het team aan de nodige expertise om dit veilig en efficiënt in te richten binnen de tijdlijn van het project.

## Consequenties

**Ontwikkeling:** Het team moet kennis hebben van Spring Boot en Dependency Injection.

**Beheer:** Er moet een RabbitMQ-server en MongoDB-server worden ingericht en onderhouden (naast de applicatie zelf).

**Beveiliging:** Omdat we Java 8 gebruiken, moeten we extra goed letten op het up-to-date houden van dependencies om beveiligingslekken te voorkomen.
