#!/usr/bin/env bash
# Generates self-signed PKCS12 keystores for local development.
# Run once before building: ./generate-dev-certs.sh
# The keystores are gitignored and bundled into the JAR at build time.

set -euo pipefail

PASSWORD="${SSL_KEY_STORE_PASSWORD:-changeit}"
VALIDITY=365

MODULES=(api notifworker)

for MODULE in "${MODULES[@]}"; do
  DEST="${MODULE}/src/main/resources/keystore.p12"
  echo "Generating keystore for ${MODULE} → ${DEST}"

  keytool -genkeypair \
    -alias azaricomm \
    -keyalg EC \
    -groupname secp256r1 \
    -sigalg SHA256withECDSA \
    -validity "${VALIDITY}" \
    -storetype PKCS12 \
    -keystore "${DEST}" \
    -storepass "${PASSWORD}" \
    -dname "CN=azaricomm-${MODULE}, OU=Dev, O=AzariComm, L=Breda, C=NL" \
    -noprompt

  echo "  ✓ ${DEST}"
done

echo ""
echo "Done. Run 'docker-compose up --build' (or 'mvn package') to bundle the keystores."
