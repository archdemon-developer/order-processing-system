#!/bin/bash
set -e

export PATH="/usr/bin:$PATH"

echo "Waiting for Kafka to be ready..."

BOOTSTRAP="kafka:9094"
ADMIN_CONFIG="/tmp/admin-client.properties"

cat > $ADMIN_CONFIG <<EOF
security.protocol=PLAINTEXT
EOF

echo "Creating SCRAM user: ${KAFKA_ADMIN_USERNAME}"
kafka-configs --bootstrap-server $BOOTSTRAP \
  --command-config $ADMIN_CONFIG \
  --alter \
  --add-config "SCRAM-SHA-512=[iterations=8192,password=${KAFKA_ADMIN_PASSWORD}]" \
  --entity-type users \
  --entity-name ${KAFKA_ADMIN_USERNAME}

echo "Creating SCRAM user: ${KAFKA_CLIENT_USERNAME}"
kafka-configs --bootstrap-server $BOOTSTRAP \
  --command-config $ADMIN_CONFIG \
  --alter \
  --add-config "SCRAM-SHA-512=[iterations=8192,password=${KAFKA_CLIENT_PASSWORD}]" \
  --entity-type users \
  --entity-name ${KAFKA_CLIENT_USERNAME}

echo "SCRAM users created successfully."