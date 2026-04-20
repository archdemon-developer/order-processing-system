#!/bin/bash

BOOTSTRAP_SERVER="kafka:9092"
SCRIPT_DIR="$(dirname "$0")"

while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ ^#.*$ || -z "$line" ]] && continue
    topic=$(echo "$line" | awk '{print $1}')
    retention=$(echo "$line" | awk '{print $2}')
    kafka-topics --bootstrap-server $BOOTSTRAP_SERVER \
        --create --if-not-exists \
        --topic "$topic" \
        --replication-factor 1 \
        --partitions 1 \
        --config retention.ms="$retention"
done < "$SCRIPT_DIR/topics.conf"