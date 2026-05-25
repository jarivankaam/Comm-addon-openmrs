# ADR 04: Kwaliteitsbewaking en Teststrategie

**Status:** Geaccepteerd 
**Datum:** 23 april 2026  
**Besluitvormer:** Architectuurteam

## Context
De communicatiemodule verwerkt privacygevoelige medische gegevens. Een fout in de code kan leiden tot datalekken of het niet aankomen van kritieke afspraakinformatie. We moeten een strategie bepalen die de kwaliteit waarborgt, voldoet aan de beveiligingseisen van de opdrachtgever, maar die ook uitvoerbaar blijft binnen de tijd van het project.

## Besluit
Wij kiezen voor een benadering van kwaliteit door controles zo vroeg mogelijk in het proces te automatiseren. Dit doen we via:
1.  **SonarCloud:** Automatische code-analyse bij elke Pull Request (CI/CD).
2.  **Testaanpak:** Focus op Unit- en Integratietesten.
3.  **Security Focus:** Uitvoeren van Pentesten in plaats van volledige ketentesten.

| Testtype| Doel |
| :--- | :--- |
| **Unittesten** |  Controleren van de logica (bijv. de 24u/1u verzendtijd berekening). |
| **Integratietesten** | Controleren of de Java-code goed praat met MongoDB en RabbitMQ. |
| **Pentesten** | Handmatig en geautomatiseerd zoeken naar lekken (AES/TLS checks). |

## Argumentatie

### 1. SonarCloud & CI/CD
Door een SonarCloud-bot aan onze Pull Requests toe te voegen, creëren we een automatische kwaliteitsbewaking. 
* **Vroegtijdige detectie:** De bot markeert direct "code smells" en potentiële bugs voordat de code überhaupt wordt samengevoegd.
* **Security Gate:** SonarCloud controleert op bekende kwetsbaarheden en zorgt dat we geen hardcoded credentials (zoals API-keys voor SwiftSend) per ongeluk in de repository zetten.

### 2. Focus op Unit & Integratie
Door de kern van de applicatie (de logica) te dekken met Unittesten en de verbindingen met de database en wachtrij met Integratietesten, vangen we veel van de fouten af zonder afhankelijk te zijn van externe systemen.

### 3. Pentesten voor Compliance
Omdat de opdrachtgever expliciet vraagt om zware beveiliging (AES-256 en TLS 1.3), volstaan standaard functionele testen niet. Met Pentesten kijken we specifiek of data in de logs lekt en of de encryptie-eisen technisch waterdicht zijn.

## Overwogen alternatieven

### Full End-to-End (E2E) Testen (Rejected)
We hebben overwogen om de volledige keten (van OpenMRS tot aan een echte telefoon) te testen, maar dit hebben we afgewezen om de volgende redenen:
* **Kosten:** Externe providers zoals SwiftSend of SecurePost brengen kosten in rekening per verstuurd bericht.
* **Onbetrouwbaarheid:** We hebben geen controle over de uptime van de fictieve externe API's. Falende testen zouden dan vaak niet door onze eigen code komen, maar door de provider.
* **Complexiteit:** Het automatisch uitlezen van een SMS of WhatsApp-bericht op een fysieke telefoon is technisch zeer complex om te automatiseren.
* **Oplossing:** In plaats van E2E-testen gebruiken we "Mocks" (namaak-providers) in onze integratietesten.

## Consequenties
* **Ontwikkeling:** Ontwikkelaars kunnen hun code niet mergen als SonarCloud op "rood" staat. Dit dwingt een hoge standaard af.
* **Onderhoud:** De mocks voor de vier providers (SwiftSend, LegacyLink, AsyncFlow en SecurePost) moeten actueel worden gehouden als de API-specificaties veranderen.
* **Veiligheid:** Door de focus op Pentesten is de kans op het lekken van medische data minimaal, wat cruciaal is voor de HL7 FHIR-compliance.
