# ADR 1: Positionering van de Communicatiemodule

**Status:** Geaccepteerd

**Datum:** 23 april 2026

**Besluitvormer:** Architectuurteam

## Context

OpenMRS-organisaties wereldwijd hebben behoefte aan een manier om patiënten te informeren via messaging-providers (zoals WhatsApp of SMS). Omdat OpenMRS vaak lokaal draait in klinieken met beperkte technische middelen, is het beheer van complexe API-koppelingen en beveiligingscertificaten voor deze klinieken een grote last. Er moet een keuze worden gemaakt: bouwen we dit in OpenMRS (als Java-module) of buiten OpenMRS (als zelfstandige SaaS-oplossing)?

## Besluit

Wij kiezen voor een **zelfstandige SaaS-module** die via een beveiligde API (HL7 FHIR) communiceert met één of meerdere OpenMRS-instanties.

## Argumentatie

De keuze voor een zelfstandige module is gebaseerd op de volgende punten:

**Onafhankelijkheid van OpenMRS-versies:** De opdracht vereist ondersteuning vanaf versie 2.7.x. Door het los te koppelen, hoeven we de code niet aan te passen aan de specifieke interne API's van elke OpenMRS-versie.

**SaaS-schaalbaarheid:** Als zelfstandig product kunnen we updates (zoals een nieuwe provider-koppeling) in één keer uitrollen voor alle organisaties, zonder dat zij hun lokale OpenMRS-server hoeven te herstarten of updaten.

**Ontkoppeling van resources:** Het verwerken van grote hoeveelheden berichten (met retry-mechanismen en encryptie) vraagt veel CPU en geheugen. Door dit buiten OpenMRS te houden, blijft de kritieke patiëntenzorg-software in de kliniek stabiel en snel.

**Beveiliging & compliance:** Gevoelige gegevens (zoals API-keys van providers) worden centraal en versleuteld opgeslagen in een gecontroleerde SaaS-omgeving, in plaats van op mogelijk onveilige lokale servers in klinieken.

## Overwogen alternatieven

### 1. Ingebouwde OpenMRS Module *(Rejected)*

**Waarom niet?** Elke update aan een provider (bijv. een API-wijziging bij SwiftSend) zou betekenen dat elke kliniek handmatig een nieuwe module-versie moet installeren. Daarnaast is het beheren van 'secrets' en encryptie op honderden verschillende lokale installaties een enorm beveiligingsrisico.

**Gevolg:** Hoge beheerslast voor de klinieken en trage adoptie van nieuwe functies.

### 2. Directe integratie via database-triggers *(Rejected)*

**Waarom niet?** Dit doorbreekt alle standaarden (zoals HL7 FHIR) en maakt de module extreem afhankelijk van de database-structuur van OpenMRS.

**Gevolg:** Bij elke kleine database-update van OpenMRS zou de communicatiemodule kapotgaan.

## Consequenties

**Positief:** Centraal beheer, eenvoudigere monitoring via OpenTelemetry, en de mogelijkheid om organisaties te laten betalen per abonnement (SaaS-model).

**Negatief:** Er moet een veilige netwerkverbinding (TLS 1.3) tot stand worden gebracht tussen de lokale OpenMRS-instantie en de SaaS-module, wat configuratie van firewalls in de kliniek kan vereisen.

**Risico:** Als de SaaS-module offline is, kunnen er geen berichten worden verstuurd. Dit vangen we op door een robuust queueing-mechanisme aan de ontvangende kant van de module.
