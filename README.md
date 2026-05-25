# AzariComm - OpenMRS Communication Architecture

A decoupled, SaaS communication platform that receives appointment events from OpenMRS and routes them to external messaging providers.

## Architecture

The project has transitioned from a monolithic OpenMRS module to a decoupled microservices architecture to improve reliability, monitoring, and separation of concerns.

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

**Components:**
1. **Appointment Webhook Module** (`appointmentwebhook/`): An OpenMRS module that hooks into Bahmni Appointments. When an appointment is saved or updated, it pushes a FHIR R4 webhook payload to the AzariComm API.
2. **AzariComm API** (`azaricomm/api`): A Spring Boot service that receives webhook payloads, validates signatures, and persists appointment records into MongoDB.
3. **AzariComm Scheduler** (`azaricomm/scheduler`): A Spring Boot service that periodically checks MongoDB for upcoming appointments and publishes event payloads to RabbitMQ.
4. **AzariComm Notification Worker** (`azaricomm/notifworker`): Consumes messages from RabbitMQ and routes them to the correct external messaging provider.
5. **FakeComWorld** (`azaricomm/fakecomworld`): A mock messaging provider container used for local testing of SwiftSend, LegacyLink, SecurePost, and AsyncFlow.

**Telemetry & Monitoring**: The stack includes OpenTelemetry Collector, Tempo, Prometheus, and Grafana to provide full-system tracing and metrics.

## Quick Start

### Prerequisites
- Docker and Docker Compose
- Java 17+ and Maven (for building the OpenMRS webhook module, if desired)

### Start the AzariComm Platform

```bash
cd azaricomm
docker compose up --build
```

This starts:
- **API** (`:8080`)
- **Scheduler**
- **Notifworker**
- **RabbitMQ** (`:5672`, Management UI `:18072`)
- **MongoDB** (`:27017`)
- **FakeComWorld** (`:1337`)
- **Monitoring Stack**: Grafana (`:3000`), Prometheus (`:9090`), Tempo (`:3200`)

### Send a Test Notification

You can simulate what the scheduler does by pushing a test payload directly to the API endpoints or by using the included script:

```bash
cd azaricomm
chmod +x test-notification.sh
./test-notification.sh swiftsend
```

### Verify

Watch the logs:

```bash
docker compose logs -f api notifworker
```

### Supported Messaging Providers (via NotifWorker)

| Provider    | Behaviour                              |
|-------------|----------------------------------------|
| SwiftSend   | Fast synchronous REST API              |
| LegacyLink  | Slower legacy API                      |
| AsyncFlow   | Async acceptance, delivers later       |
| SecurePost  | Security-focused with encryption       |

You can test them individually by passing the provider name to the script:
```bash
./test-notification.sh legacylink
./test-notification.sh asyncflow
./test-notification.sh securepost
```

## Adding a New Provider

1. Open the `azaricomm/notifworker/` project and create a class implementing the `MessagingProvider` interface.
2. Annotate it with `@Component`.
3. Return a unique name from `getName()`.
4. Implement the `send()` logic.

The `ProviderRouter` will auto-discover the new implementation.

## OpenMRS Module Setup (appointmentwebhook)

To connect an existing OpenMRS instance with Bahmni Appointments to this platform:
1. Build the module: `cd appointmentwebhook && mvn clean install`
2. Install the generated OMOD into your OpenMRS server.
3. Configure the webhook secret and destination URL (e.g., `http://azaricomm-api:8080/api/appointments/webhook/openmrs`) via the OpenMRS Global Properties.
