#!/bin/bash
# test-notification.sh
# Sends a test notification via the API service.
# Requires: docker compose up (all services must be running)
#
# Usage: ./test-notification.sh [provider]
# Example: ./test-notification.sh swiftsend

PROVIDER=${1:-swiftsend}
API_URL="http://localhost:8080/api/notifications"

echo "Sending test notification via provider: $PROVIDER"

curl -s -X POST "$API_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "organizationId": "hospital-amsterdam-001",
    "patientId": "patient-12345",
    "patientPhone": "+31612345678",
    "subject": "Afspraakherinnering",
    "body": "Uw afspraak is morgen om 10:00 op de polikliniek.",
    "appointmentId": "appt-67890",
    "appointmentDateTime": "2026-05-12T10:00:00",
    "appointmentLocation": "Polikliniek 3, Kamer 201",
    "instructions": "Nuchter blijven vanaf 22:00 de avond ervoor.",
    "timezone": "Europe/Amsterdam",
    "notificationType": "REMINDER_24H",
    "provider": "'"$PROVIDER"'"
  }'

echo ""
echo ""
echo "Check logs: docker compose logs -f api communication-service"
