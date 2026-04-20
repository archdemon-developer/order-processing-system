#!/bin/bash

CONNECT_URL="http://kafka-connect-1:8083"

echo "Registering orders-db connector..."
curl -s -X POST "$CONNECT_URL/connectors" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "debezium-orders-outbox",
    "config": {
      "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
      "database.hostname": "postgres",
      "database.port": "5432",
      "database.user": "${file:/etc/kafka-connect/secrets/connect-secrets.properties:db.username}",
      "database.password": "${file:/etc/kafka-connect/secrets/connect-secrets.properties:db.password}",
      "database.dbname": "orders_db",
      "database.server.name": "orders",
      "topic.prefix": "orders",
      "table.include.list": "public.outbox_events",
      "plugin.name": "pgoutput",
      "transforms": "outbox",
      "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
      "transforms.outbox.table.field.event.key": "aggregateid",
      "transforms.outbox.route.by.field": "aggregatetype",
      "transforms.outbox.table.fields.additional.placement": "type:header:eventType",
      "key.converter": "org.apache.kafka.connect.storage.StringConverter",
      "value.converter": "org.apache.kafka.connect.storage.StringConverter"
    }
  }'

echo ""
echo "Registering payments-db connector..."
curl -s -X POST "$CONNECT_URL/connectors" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "debezium-payments-outbox",
    "config": {
      "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
      "database.hostname": "postgres",
      "database.port": "5432",
      "database.user": "${file:/etc/kafka-connect/secrets/connect-secrets.properties:db.username}",
      "database.password": "${file:/etc/kafka-connect/secrets/connect-secrets.properties:db.password}",
      "database.dbname": "payments_db",
      "database.server.name": "payments",
      "topic.prefix": "payments",
      "table.include.list": "public.outbox_events",
      "plugin.name": "pgoutput",
      "transforms": "outbox",
      "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
      "transforms.outbox.table.field.event.key": "aggregateid",
      "transforms.outbox.route.by.field": "aggregatetype",
      "transforms.outbox.table.fields.additional.placement": "type:header:eventType",
      "key.converter": "org.apache.kafka.connect.storage.StringConverter",
      "value.converter": "org.apache.kafka.connect.storage.StringConverter"
    }
  }'

echo ""
echo "Done."