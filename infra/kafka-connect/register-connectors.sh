#!/bin/sh

CONNECT_URL="http://kafka-connect-1:8083"

echo "Registering orders-db connector..."
echo "POSTGRES_USER is: $POSTGRES_USER"

curl -s -X POST "$CONNECT_URL/connectors" \
  -H "Content-Type: application/json" \
  -d "$(cat <<EOF
{
  "name": "debezium-orders-outbox",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "postgres",
    "database.port": "5432",
    "database.user": "$POSTGRES_USER",
    "database.password": "$POSTGRES_PASSWORD",
    "database.dbname": "orders_db",
    "database.server.name": "orders",
    "topic.prefix": "orders",
    "table.include.list": "public.outbox_events",
    "slot.name": "debezium_orders",
    "plugin.name": "pgoutput",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.key": "aggregateid",
    "transforms.outbox.route.topic.replacement": "\${routedByValue}",
    "transforms.outbox.route.by.field": "aggregatetype",
    "transforms.outbox.table.fields.additional.placement": "type:header:eventType",
    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter": "org.apache.kafka.connect.storage.StringConverter"
  }
}
EOF
)"

echo ""
echo "Registering payments-db connector..."
curl -s -X POST "$CONNECT_URL/connectors" \
  -H "Content-Type: application/json" \
  -d "$(cat <<EOF
{
  "name": "debezium-payments-outbox",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "postgres",
    "database.port": "5432",
    "database.user": "$POSTGRES_USER",
    "database.password": "$POSTGRES_PASSWORD",
    "database.dbname": "payments_db",
    "database.server.name": "payments",
    "topic.prefix": "payments",
    "table.include.list": "public.outbox_events",
    "slot.name": "debezium_payments",
    "plugin.name": "pgoutput",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.route.topic.replacement": "\${routedByValue}",
    "transforms.outbox.table.field.event.key": "aggregateid",
    "transforms.outbox.route.by.field": "aggregatetype",
    "transforms.outbox.table.fields.additional.placement": "type:header:eventType",
    "key.converter": "org.apache.kafka.connect.storage.StringConverter",
    "value.converter": "org.apache.kafka.connect.storage.StringConverter"
  }
}
EOF
)"

echo ""
echo "Done."