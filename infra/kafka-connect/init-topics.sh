#!/bin/bash
set -e

CONFIG=/tmp/client.properties
cat > $CONFIG <<EOF
security.protocol=SASL_PLAINTEXT
sasl.mechanism=SCRAM-SHA-512
sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required username="${KAFKA_ADMIN_USERNAME}" password="${KAFKA_ADMIN_PASSWORD}";
EOF

BOOTSTRAP_SERVER="kafka:9092"
SCRIPT_DIR="$(dirname "$0")"

while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ ^#.*$ || -z "$line" ]] && continue
    topic=$(echo "$line" | awk '{print $1}')
    retention=$(echo "$line" | awk '{print $2}')
    kafka-topics --bootstrap-server $BOOTSTRAP_SERVER \
        --create --if-not-exists \
        --command-config $CONFIG \
        --topic "$topic" \
        --replication-factor 1 \
        --partitions 1 \
        --config retention.ms="$retention"
done < "$SCRIPT_DIR/topics.conf"