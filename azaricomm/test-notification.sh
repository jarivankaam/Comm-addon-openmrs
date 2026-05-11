#!/bin/bash
# test-notification.sh
# Publishes a test notification directly to RabbitMQ via its HTTP API.
# Requires: docker compose up (RabbitMQ must be running)
#
# Usage: ./test-notification.sh [provider]
# Example: ./test-notification.sh swiftsend

PROVIDER=${1:-swiftsend}
RABBITMQ_URL="http://localhost:15672/api/exchanges/%2F/openmrs.events/publish"

echo "Publishing test notification via provider: $PROVIDER"

curl -s -u guest:guest \
  -H "Content-Type: application/json" \
  -X POST "$RABBITMQ_URL" \
  -d '{
    "properties": {
      "content_type": "text/plain"
    },
    "routing_key": "notification.REMINDER_24H",
    "payload": "{\"organizationId\":\"hospital-amsterdam-001\",\"patientUuid\":\"patient-uuid-12345\",\"patientPhone\":\"+31612345678\",\"subject\":\"Afspraakherinnering\",\"body\":\"Uw afspraak is morgen om 10:00 op de polikliniek.\",\"appointmentUuid\":\"appt-uuid-67890\",\"appointmentDateTime\":\"2026-05-12T10:00:00\",\"appointmentLocation\":\"Polikliniek 3, Kamer 201\",\"instructions\":\"Nuchter blijven vanaf 22:00 de avond ervoor.\",\"timezone\":\"Europe/Amsterdam\",\"notificationType\":\"REMINDER_24H\",\"provider\":\"'"$PROVIDER"'\"}",
    "payload_encoding": "string"
  }'

echo ""
echo ""
echo "Check the communication-service logs: docker compose logs -f communication-service"
