# AzariComm - OpenMRS Communicatiearchitectuur

Een ontkoppeld SaaS-communicatieplatform dat afspraakgebeurtenissen vanuit OpenMRS ontvangt en doorstuurt naar externe berichtenproviders.

## Architectuur

Het project is overgegaan van een monolithische OpenMRS-module naar een ontkoppelde microservicesarchitectuur, met als doel de betrouwbaarheid, monitoring en scheiding van verantwoordelijkheden te verbeteren.

```text
┌───────────────────────────┐    ┌────────────────────────────────────────────────────────┐
│        OpenMRS            │    │                       AzariComm                        │
│ ┌───────────────────────┐ │    │                                                        │
│ │ Bahmni Appointments   │ │ 1  │ ┌─────────┐   2  ┌──────────┐   3  ┌───────────────┐ │
│ │ Hook Module           ├─┼────┼▶│   API   │─────▶│ MongoDB  │◀─────┤   Scheduler   │ │
│ │ (appointmentwebhook)  │ │    │ └─────────┘      └──────────┘      │               │ │
│ └───────────────────────┘ │    │                                    └───────┬───────┘ │
└───────────────────────────┘    │                                            │         │
                                 │                                          4 │ publish │
                                 │                                            ▼         │
                                 │  ┌────────────────────┐                ┌───────────┐ │
                                 │  │  FakeComWorld      │   6   consume  │ RabbitMQ  │ │
                                 │  │ (Messaging Mock)   │◀─ ─ ─ ─ ─ ─ ─ ─┤           │ │
                                 │  └────────────────────┘ ┌───────────┐  └───────────┘ │
                                 │                         │Notifworker│                │
                                 │                         └───────────┘                │
                                 └────────────────────────────────────────────────────────┘
```

**Componenten:**
1. **Appointment Webhook Module** (`appointmentwebhook/`): Een OpenMRS-module die zich koppelt aan Bahmni Appointments. Wanneer een afspraak wordt opgeslagen of gewijzigd, verstuurt de module een FHIR R4-webhookpayload naar de AzariComm API.
2. **AzariComm API** (`azaricomm/api`): Een Spring Boot-service die webhookpayloads ontvangt, handtekeningen valideert en afspraakgegevens opslaat in MongoDB.
3. **AzariComm Scheduler** (`azaricomm/scheduler`): Een Spring Boot-service die periodiek MongoDB controleert op aankomende afspraken en bijbehorende events publiceert naar RabbitMQ.
4. **AzariComm Notification Worker** (`azaricomm/notifworker`): Verwerkt berichten uit RabbitMQ en stuurt ze door naar de juiste externe berichtenprovider.
5. **FakeComWorld** (`azaricomm/fakecomworld`): Een nep-berichtenprovider als container, bedoeld voor lokaal testen van SwiftSend, LegacyLink, SecurePost en AsyncFlow.

**Telemetrie & Monitoring**: De stack bevat een OpenTelemetry Collector, Tempo, Prometheus en Grafana voor volledige tracing en metriekenverzameling over het hele systeem.

## Snel starten

### Vereisten
- Docker en Docker Compose
- Java 17+ en Maven (alleen nodig als je de OpenMRS-webhookmodule zelf wilt bouwen)

### Het AzariComm-platform starten

```bash
cd azaricomm
docker compose up --build
```

Dit start de volgende services:
- **API** (`:8080`)
- **Scheduler**
- **Notifworker**
- **RabbitMQ** (`:5672`, beheerinterface op `:18072`)
- **MongoDB** (`:27017`)
- **FakeComWorld** (`:1337`)
- **Monitoringstack**: Grafana (`:3000`), Prometheus (`:9090`), Tempo (`:3200`)

### Een testmelding versturen

Je kunt een afspraak simuleren door een verzoek rechtstreeks naar de API te sturen. Pas `scheduledTime` aan naar een tijdstip in de toekomst en kies een ondersteunde providernaam:

```bash
curl -X POST http://localhost:8080/api/appointments \
  -H "Content-Type: application/json" \
  -d '{
    "organizationId": "org-001",
    "scheduledTime": "2026-12-01T10:00:00Z",
    "patientId": "patient-123",
    "patientPhone": "+31612345678",
    "subject": "Vervolgconsult",
    "location": "Kamer 4B",
    "provider": "swiftsend",
    "timezone": "Europe/Amsterdam"
  }'
```

### Verifiëren

Bekijk de loguitvoer:

```bash
docker compose logs -f api notifworker
```

### Ondersteunde berichtenproviders (via NotifWorker)

| Provider    | Gedrag                                        |
|-------------|-----------------------------------------------|
| SwiftSend   | Snelle synchrone REST API                     |
| LegacyLink  | Tragere legacy API                            |
| AsyncFlow   | Asynchrone verwerking, aflevering volgt later |
| SecurePost  | Beveiligingsgericht met versleuteling         |

Om een andere provider te testen, pas je het veld `"provider"` in de requestbody aan naar `legacylink`, `asyncflow` of `securepost`.

## Een nieuwe provider toevoegen

1. Open het project `azaricomm/notifworker/` en maak een klasse aan die de interface `MessagingProvider` implementeert.
2. Annoteer de klasse met `@Component`.
3. Geef een unieke naam terug vanuit `getName()`.
4. Implementeer de verzendlogica in de methode `send()`.

De `ProviderRouter` ontdekt de nieuwe implementatie automatisch.

## OpenMRS-module instellen (appointmentwebhook)

Om een bestaande OpenMRS-installatie met Bahmni Appointments te koppelen aan dit platform:
1. Bouw de module: `cd appointmentwebhook && mvn clean install`
2. Installeer het gegenereerde OMOD-bestand op je OpenMRS-server.
3. Stel het webhooksecret en de doel-URL (bijv. `http://azaricomm-api:8080/api/appointments/webhook/openmrs`) in via de OpenMRS Global Properties.
