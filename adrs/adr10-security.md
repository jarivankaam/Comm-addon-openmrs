# ADR 10: Beveiliging, Gegevensversleuteling en Endpoint Validatie

**Status:** Geaccepteerd

**Datum:** 23 mei 2026

**Besluitvormer:** Architectuurteam

## Context

Als SaaS-applicatie in de zorgsector verwerkt onze communicatiemodule medische gegevens en persoonsgegevens (PII). Dit stelt hoge eisen aan de beveiliging. We moeten garanderen dat bij een eventuele database-inbraak de patiëntgegevens onleesbaar zijn, dat netwerkverkeer niet kan worden afgeluisterd en dat onbetrouwbare systemen geen valse afspraken kunnen inschieten via onze publieke API.

## Besluit

Wij implementeren een 'Defense-in-Depth' beveiligingsstrategie bestaande uit vier harde pijlers:
1. **Database-encryptie (Crypto-Blob & Losse Encryptie):** Opslag van gevoelige velden in een versleutelde binaire blob, gecombineerd met een afzonderlijk versleuteld locatieveld.
2. **Inkomende en uitkomende Validatie:** Filteren van inkomende payloads en het minimaliseren van API-responses (Data Minimization).
3. **Transportbeveiliging:** Verplicht gebruik van TLS 1.3 voor al het netwerkverkeer.
4. **Webhook Authenticiteit:** Verificatie van inkomende OpenMRS-webhooks middels HMAC-SHA256 handtekeningen.
5. **Data Retentie:** Technische garantie dat alle privacygevoelige patiëntgegevens maximaal 14 dagen na de feitelijke afspraaktijd uit het systeem worden gewist.

## Argumentatie

### 1. Database-encryptie (AES-256)
Om te voorkomen dat een aanvaller bij een database-lek direct patronen kan herkennen of data kan herleiden, passen we een encryptiestructuur toe:
* **De 'Crypto-Blob':** De meest privacygevoelige data (`patientPhone`, `instructions`, `provider`, `subject`) worden samengevoegd in één JSON-object en als één onleesbare binaire blob opgeslagen. Een hacker kan hierdoor aan de buitenkant niet eens zien *welke* velden er aanwezig zijn.
* **Geïsoleerde Locatie-encryptie:** Het veld `location` wordt *los* versleuteld. Dit stelt de applicatie in staat om (indien nodig in de toekomst) database-optimalisaties te doen, zonder de gehele hoofd-blob te hoeven ontsleutelen.

### 2. Inkomende en uitkomende Validatie (Data Minimization)
* **Ingress:** Alle inkomende JSON-payloads worden direct streng gevalideerd met behulp van Jakarta Validation (`@Valid`). Voldoet een bericht niet aan de syntaxis (bijv. een ongeldige status), dan wordt het direct geweigerd.
* **Egress:** Onze API-responses sturen *nooit* de volledige database-inhoud terug naar OpenMRS. Gevoelige data die al bekend is bij OpenMRS wordt weggelaten uit de response. We sturen enkel een minimale statusbevestiging terug. Dit voorkomt onbewuste datalekken via API-responses.

### 3. Transportbeveiliging (TLS 1.3)
Alle HTTP-communicatie tussen de OpenMRS-systemen, onze SaaS-API, RabbitMQ en de externe messaging providers is verplicht versleuteld met **TLS 1.3**. Dit is de modernste standaard die man-in-the-middle (MITM) aanvallen onmogelijk maakt en snellere handshakes oplevert dan oudere TLS-versies.

### 4. Webhook Authenticiteit (HMAC-SHA256 Signature)
Omdat ons webhook-endpoint publiek bereikbaar moet zijn om berichten van OpenMRS te ontvangen, moeten we requests authenticeren. 
* De OpenMRS-plugin berekent een cryptografische handtekening (hash) over de body van het bericht met behulp van een geheime sleutel en stuurt deze mee in de `X-Webhook-Signature` header.
* Onze API herberekent deze hash met behulp van **HMAC-SHA256**. Komen de handtekeningen niet overeen? Dan wordt het verzoek direct geweigerd met een `412 Precondition Failed` of `401 Unauthorized`. Dit garandeert de authenticiteit en integriteit van de data.

### 5. Strikte Data Retentie & Privacy-Lifecycle (Eis 10)
Om te voldoen aan de wetgeving rondom dataminimalisatie, mag patiëntdata niet langer worden bewaard dan noodzakelijk voor het logistieke proces.
* **MongoDB TTL-Index:** We configureren een automatische Time-To-Live (TTL) index op de `Appointments` collectie die gekoppeld is aan het veld `expireAt` (dat is gebaseerd op de `scheduledTime`).
* **Garantie na gebruik:** Exact 14 dagen *nadat* de afspraak heeft plaatsgevonden, verwijdert de database-engine het document automatisch. Hiermee automatiseren we de privacy-compliance direct op databaseniveau, zonder dat we hiervoor foutgevoelige handmatige scripts hoeven te draaien.
* **Anonieme Audit-trail:** De facturatiegegevens (in `Audit_logs`) blijven conform de wettelijke bewaarplicht 1 jaar bewaard, maar bevatten door de strikte scheiding (zie **ADR 7**) geen enkele referentie of herleidbare PII meer zodra het appointment-document via de TTL-index is vernietigd.

## Overwogen alternatieven

**Volledige Database Encryptie (TDE - Transparent Data Encryption)** *(Rejected)*:
* **Waarom niet:** TDE versleutelt de database op schijfniveau. Zodra de database-engine draait, is de data voor iedereen met database-toegang alsnog in platte tekst leesbaar. Onze gelaagde applicatie-level encryptie (Crypto-Blob) garandeert dat de data *altijd* versleuteld blijft, zelfs voor een database-administrator (DBA) zonder de juiste applicatie-sleutel.

## Consequenties

* **Sleutelbeheer (Key Management):** De Worker en de API moeten veilig toegang krijgen tot de cryptografische sleutels. Deze sleutels mogen nooit in de Git-repository belanden.
* **CPU Overhead:** Het ontsleutelen van de blob vlak voor verzending in de Worker kost extra processor-kracht. Omdat de Workers horizontaal schaalbaar zijn (zie **ADR 6**), vormt dit echter geen risico voor de totale systeemprestaties.
