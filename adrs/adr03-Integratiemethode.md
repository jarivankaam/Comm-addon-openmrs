# ADR 3: Integratiemethode (Koppeling met OpenMRS via Custom Plugin)

**Status:** Geaccepteerd 

**Datum:** 21 mei 2026

**Besluitvormer:** Architectuurteam

## Context

De module staat als SaaS in de cloud, terwijl de OpenMRS-systemen van ziekenhuizen verspreid over de wereld staan. We moeten een manier vinden om afspraakgegevens (tijd, locatie, instructies) van die ziekenhuizen naar onze module te krijgen, zonder dat we het ziekenhuisnetwerk onnodig zwaar belasten of berichten missen.

## Besluit

Wij kiezen voor een **Event-Driven Push-koppeling middels een Custom OpenMRS-plugin**.

We hebben een eigen plugin gebouwd die binnen OpenMRS 'luistert' naar het opslaan van een afspraak. Zodra de dokter een afspraak opslaat, activeert de plugin, verzamelt de metadata en stuurt direct een HTTP POST-request (webhook) naar ons centrale API-endpoint.

## Argumentatie

### 1. Waarom deze methode?

* **Gegarandeerde Real-time verwerking:** Zodra een medewerker op 'opslaan' klikt in OpenMRS, reageert de plugin direct. Er is zelden vertraging en we hoeven niet periodiek de database te pollen.
* **Gecontroleerde Payload & Minder Netwerkoverhead:** In plaats van zware, generieke FHIR-datastructuren te synchroniseren via polling, filtert de plugin direct aan de bron de exacte afspraakgegevens (tijd, datum, locatie, instructies) en stuurt alleen het noodzakelijke pakketje door.
* **On-demand Verrijking (Privacy-by-Design):** De plugin stuurt in eerste instantie de afspraak-metadata. Onze API ontvangt dit, valideert de authenticiteit, en haalt vervolgens gericht en veilig de noodzakelijke contactgegevens (zoals het telefoonnummer) van de patiënt op via de OpenMRS API. Hierdoor minimaliseren we de hoeveelheid privacygevoelige data die onnodig over de lijn gaat.

### 2. Wat als er iets plat ligt? (Downtime & Fallback)

* **Onze API-module ligt eruit:** Als onze SaaS-module offline is, vangt de OpenMRS-plugin dit op. De plugin wordt uitgerust met een lokaal retry-mechanisme (bijv. een exponentieel back-off schema) om het bericht opnieuw aan te bieden zodra de API weer bereikbaar is.
* **OpenMRS ligt eruit:** Als een lokaal OpenMRS-systeem offline gaat, worden er geen afspraken gemaakt. Zodra het systeem opstart en synchroniseert, triggert de plugin de openstaande events alsnog.

### 3. Technische Flow van de Data

1. **Triggerelement:** Een medewerker slaat een afspraak op in OpenMRS.
2. **Plugin Interceptie:** De custom plugin haakt in op het persist-event, extraheert de afspraak-payload en stuurt een beveiligde HTTP POST-request naar `/api/appointments/webhook/openmrs`.
3. **API Validatie & Verrijking:** Onze API ontvangt het JSON-pakket, controleert de cryptografische handtekening (HMAC-SHA256) en valideert de syntaxis. Vervolgens haalt de API via een beveiligde client-call het telefoonnummer van de patiënt op.
4. **Database Opslag:** Als alle checks groen zijn, versleutelt de API de PII (AES-256) en slaat het document op in MongoDB met de status `SCHEDULED`.

## Overwogen alternatieven

* **Standaard FHIR Subscriptions (Rest Hooks)** *(Rejected)*: Hoewel dit de HL7-standaard is, bleek de ingebouwde subscription-functionaliteit van OpenMRS te rigide. Het bood onvoldoende flexibiliteit voor aangepaste foutafhandeling (retries aan de bron) en dwong ons om direct grote hoeveelheden onversleutelde patiëntdata mee te sturen.
* **Polling via REST API** *(Rejected)*: Hierbij zou onze SaaS-module elke minuut aan duizenden ziekenhuizen moeten vragen of er al nieuws is. Dit schaalt niet, veroorzaakt gigantische netwerkoverhead en zorgt voor onnodige belasting op de lokale OpenMRS-databases.

## Consequenties

* **Onderhoud van de Plugin:** Het team is nu niet alleen verantwoordelijk voor de cloud-infrastructuur, maar ook voor het onderhoud en de compatibiliteit van de plugin binnen de OpenMRS-omgevingen van de afnemers.
* **Strikte Security op het Endpoint:** Omdat het webhook-endpoint publiekelijk bereikbaar is, is de implementatie van HMAC-SHA256 handtekening-verificatie in de controller een absolute harde eis om misbruik te voorkomen.
* **Gzip-decompressie:** Om netwerkbelasting bij grote ziekenhuizen te verminderen, moet onze API in staat zijn om met GZIP-gecomprimeerde payloads om te gaan (reeds geïmplementeerd in de controller).
