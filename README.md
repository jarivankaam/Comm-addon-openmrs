# AzariComm - OpenMRS Communication Module

A SaaS communication module that sends appointment notifications for OpenMRS organisations via external messaging providers.

## Architecture

```
┌──────────────────┐        ┌──────────┐        ┌──────────────────────────┐
│  OpenMRS + Module │──────▶│ RabbitMQ │──────▶│  Communication Service    │
│  (azaricomm)      │ publish│          │consume│                          │
└──────────────────┘        └──────────┘        │  ┌────────────────────┐  │
                                                 │  │  Provider Router   │  │
                                                 │  └─────┬──┬──┬──┬───┘  │
                                                 │        │  │  │  │      │
                                                 │   SS  LL  AF  SP      │
                                                 └──────────────────────────┘

SS = SwiftSend    LL = LegacyLink
AF = AsyncFlow    SP = SecurePost
```

The OpenMRS module publishes notification messages to RabbitMQ. The communication service consumes them asynchronously and routes to the configured messaging provider. This decouples OpenMRS from the providers — neither needs to know about the other, and downtime on either side is handled by the queue.

## Quick Start

### Prerequisites
- Docker and Docker Compose

### Start the services

```bash
docker compose up --build
```

This starts:
- **RabbitMQ** on ports 5672 (AMQP) and 15672 (management UI)
- **Communication Service** on port 8081

### Send a test notification

Once the services are running, publish a test message:

```bash
chmod +x test-notification.sh
./test-notification.sh swiftsend
```

Or use curl directly against the RabbitMQ management API:

```bash
curl -u guest:guest \
  -H "Content-Type: application/json" \
  -X POST "http://localhost:15672/api/exchanges/%2F/openmrs.events/publish" \
  -d '{
    "properties": {"content_type": "text/plain"},
    "routing_key": "notification.REMINDER_24H",
    "payload": "{\"organizationId\":\"hospital-amsterdam-001\",\"patientUuid\":\"patient-uuid-12345\",\"patientPhone\":\"+31612345678\",\"subject\":\"Afspraakherinnering\",\"body\":\"Uw afspraak is morgen om 10:00 op de polikliniek.\",\"appointmentUuid\":\"appt-uuid-67890\",\"appointmentDateTime\":\"2026-05-12T10:00:00\",\"appointmentLocation\":\"Polikliniek 3, Kamer 201\",\"instructions\":\"Nuchter blijven vanaf 22:00 de avond ervoor.\",\"timezone\":\"Europe/Amsterdam\",\"notificationType\":\"REMINDER_24H\",\"provider\":\"swiftsend\"}",
    "payload_encoding": "string"
  }'
```

### Verify

Watch the communication service logs:

```bash
docker compose logs -f communication-service
```

You should see output like:

```
Received notification from queue
Parsed notification: NotificationMessage{org='hospital-amsterdam-001', patient='patient-uuid-12345', ...}
Routing notification to provider 'swiftsend' for org 'hospital-amsterdam-001'
[SwiftSend] Sending to +31612345678 | subject: Afspraakherinnering | body: Uw afspraak is morgen...
[SwiftSend] Delivered successfully, messageId=SS-a1b2c3d4
Notification delivered: DeliveryResult{SUCCESS, provider=swiftsend, messageId=SS-a1b2c3d4}
```

### Test different providers

```bash
./test-notification.sh legacylink
./test-notification.sh asyncflow
./test-notification.sh securepost
```

### RabbitMQ Management UI

Open http://localhost:15672 (guest/guest) to inspect queues, exchanges, and messages.

## Supported Messaging Providers

| Provider    | Behaviour                              |
|-------------|----------------------------------------|
| SwiftSend   | Fast synchronous REST API              |
| LegacyLink  | Slower legacy API                      |
| AsyncFlow   | Async acceptance, delivers later       |
| SecurePost  | Security-focused with encryption       |

## Adding a New Provider

1. Create a class that implements `MessagingProvider`
2. Annotate it with `@Component`
3. Return a unique name from `getName()`

```java
@Component
public class MyNewProvider implements MessagingProvider {
    @Override
    public String getName() { return "mynewprovider"; }

    @Override
    public DeliveryResult send(NotificationMessage message) {
        // call the provider's API
        return DeliveryResult.success(getName(), "msg-id-123");
    }
}
```

No other code changes needed — the `ProviderRouter` auto-discovers it.

## Project Structure

```
azaricomm/
├── api/                        # OpenMRS module - core logic
│   └── src/main/java/.../
│       ├── messaging/
│       │   ├── RabbitMQService.java       # Publishes to RabbitMQ
│       │   ├── NotificationService.java   # Builds and sends notifications
│       │   └── NotificationMessage.java   # Shared DTO
│       ├── api/                           # OpenMRS service layer
│       └── AzariCommActivator.java
├── omod/                       # OpenMRS module - web layer
│   └── src/main/java/.../
│       └── web/controller/
│           └── NotificationController.java  # REST endpoint
├── communication-service/      # Standalone service (Spring Boot)
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/azaricomm/
│       ├── consumer/
│       │   └── NotificationConsumer.java    # RabbitMQ listener
│       ├── provider/
│       │   ├── MessagingProvider.java       # Provider interface
│       │   ├── ProviderRouter.java          # Auto-discovers and routes
│       │   ├── SwiftSendProvider.java
│       │   ├── LegacyLinkProvider.java
│       │   ├── AsyncFlowProvider.java
│       │   └── SecurePostProvider.java
│       └── model/
│           ├── NotificationMessage.java
│           └── DeliveryResult.java
├── docker-compose.yml
├── test-notification.sh
└── README.md
```
