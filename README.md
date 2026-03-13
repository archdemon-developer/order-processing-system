# Order Processing System

> A real-time, event-driven order processing platform built with a microservices architecture.  
> Designed as a portfolio project demonstrating production-grade backend engineering.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Event Flow](#event-flow)
- [Tech Stack](#tech-stack)
- [Repository Structure](#repository-structure)
- [Running Locally](#running-locally)
- [Services](#services)
- [Kafka Topics](#kafka-topics)
- [Database Schemas](#database-schemas)
- [Build Roadmap](#build-roadmap)

---

## Overview

This platform simulates a real-world order processing pipeline. A client places an order via REST, which triggers a chain of asynchronous events across five independent microservices — covering payment, inventory, notifications, and real-time analytics.

### Project Objectives

- Microservices design with clear service boundaries and independent deployability
- Event-driven communication via Apache Kafka with schema-governed contracts
- Real-time stream processing using Kafka Streams
- Full observability: distributed tracing, structured logging, and metrics
- Container-native deployment on Kubernetes (Minikube) with Helm
- Automated CI/CD via GitLab CI

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         REST Client                             │
│                    POST /api/v1/orders                          │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                      order-service                              │
│              REST API · Validation · PostgreSQL                 │
└─────────────────────────┬───────────────────────────────────────┘
                          │ order-placed
                          ▼
          ┌───────────────────────────────┐
          │                               │
          ▼                               ▼
┌──────────────────────┐     ┌────────────────────────────────────┐
│   payment-service    │     │          analytics-service          │
│  PostgreSQL + Redis  │     │         Kafka Streams              │
└──────────┬───────────┘     └────────────────────────────────────┘
           │
     ┌─────┴──────┐
     │            │
     ▼            ▼
payment-      order-
processed     failed
     │            │
     ▼            ▼
┌──────────┐  ┌──────────────────────────┐
│inventory │  │    notification-service  │
│ -service │  │     (order-failed)       │
│PostgreSQL│  └──────────────────────────┘
└────┬─────┘
     │ inventory-reserved
     ▼
┌─────────────────────────────────────────┐
│         notification-service            │
│  (payment-processed + inv-reserved)     │
└─────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────┐
│          analytics-service              │
│   (inventory-reserved aggregations)     │
└─────────────────────────────────────────┘
```

All services emit traces, metrics, and structured logs via OpenTelemetry.

```
Traces  → Tempo
Metrics → Prometheus → Grafana
Logs    → Loki
```

---

## Event Flow

1. Client sends `POST /api/v1/orders` to `order-service`
2. `order-service` validates `totalPrice = ∑(pricePerItem × quantity)` — returns HTTP 400 on mismatch
3. On success, order is persisted and `order-placed` event is published to Kafka
4. `payment-service` consumes `order-placed`:
    - **Success** → persists payment record, publishes `payment-processed`
    - **Failure** → publishes `order-failed` (explicit failure event, no ambiguous status field)
5. `inventory-service` consumes `payment-processed`, reserves stock, publishes `inventory-reserved`
6. `notification-service` operates two independent listeners:
    - `payment-processed` → "Your payment was successful."
    - `order-failed` → "Your order could not be processed."
    - `inventory-reserved` → "Your order is confirmed."
7. `analytics-service` consumes `order-placed` and `inventory-reserved` to maintain real-time aggregations

---

## Tech Stack

| Concern | Technology |
|---|---|
| Language | Kotlin (JVM 21) |
| Framework | Spring Boot 3.x |
| Build | Gradle (Kotlin DSL, multi-module) |
| Messaging | Apache Kafka |
| Serialization | Jackson (handcrafted serde) |
| Stream Processing | Kafka Streams |
| Database | PostgreSQL 17 (one instance per service) |
| DB Migrations | Flyway |
| Cache / Idempotency | Redis (payment-service only) |
| Containerisation | Docker + Docker Compose |
| Orchestration | Minikube + Helm |
| Observability | OpenTelemetry → Prometheus + Grafana + Loki + Tempo |
| CI/CD | GitLab CI |

### Serialization Decision

Apache Avro + Confluent Schema Registry was evaluated and explicitly rejected. The Confluent Schema Registry has been in maintenance mode with no meaningful updates for an extended period, introducing risk of library-level firefighting with no upstream resolution. All inter-service event contracts use handcrafted Jackson-based serialization via `EventSerializer` and `EventDeserializer` in the `shared` module.

---

## Repository Structure

```
order-processing-system/
│
├── shared/                         # Shared domain model, events, serialization
│   └── src/main/kotlin/com/orderprocessing/shared/
│       ├── envelope/               # EventEnvelope wrapper
│       ├── events/                 # OrderPlaced, PaymentProcessed, InventoryReserved, OrderFailed
│       ├── model/                  # OrderItem
│       └── serialization/          # EventSerializer, EventDeserializer
│
├── order-service/                  # REST API, order persistence, order-placed producer
├── payment-service/                # order-placed consumer, idempotency, payment producer
├── inventory-service/              # payment-processed consumer, reservation, inventory producer
├── notification-service/           # Terminal event consumers, mock notifications
├── analytics-service/              # Kafka Streams topology, real-time aggregations
│
├── infra/                          # All infrastructure configuration
│   ├── docker-compose.yml          # Local dev stack (Kafka, PostgreSQL, Redis)
│   └── postgres/
│       └── init.sql                # Creates orders_db, payments_db, inventory_db
│
├── build.gradle.kts                # Root Gradle build
├── settings.gradle.kts             # Multi-module project definition
└── README.md
```

---

## Running Locally

### Prerequisites

- Docker Engine 29+
- Docker Compose v5+
- JDK 21
- Gradle (or use the `./gradlew` wrapper)

### Start the infrastructure

```bash
cd infra
docker compose up -d
```

This starts:
- **Kafka** — single broker in KRaft mode, port `9092`
- **PostgreSQL** — single container with three databases, port `5432`
- **Redis** — single instance, port `6379`

### Verify

```bash
docker compose ps
```

All three containers should show `Up`.

Verify PostgreSQL databases:

```bash
docker exec -it postgres psql -U postgres -c "\l"
```

Expected: `orders_db`, `payments_db`, `inventory_db` all present.

Verify Redis:

```bash
docker exec -it redis redis-cli ping
# Expected: PONG
```

### Stop

```bash
docker compose down
```

To also wipe the PostgreSQL volume:

```bash
docker compose down -v
```

---

## Services

| Service | Port | Database | Responsibility |
|---|---|---|---|
| `order-service` | 8080 | `orders_db` | Accepts orders via REST, validates total price, persists, publishes `order-placed` |
| `payment-service` | 8081 | `payments_db` + Redis | Consumes `order-placed`, simulates payment, publishes result event |
| `inventory-service` | 8082 | `inventory_db` | Consumes `payment-processed`, reserves stock, publishes `inventory-reserved` |
| `notification-service` | 8083 | None | Consumes terminal events, sends mock notifications |
| `analytics-service` | 8084 | Kafka Streams store | Consumes events, maintains real-time aggregations |

### REST API (`order-service` only)

All other services are purely event-driven and expose no HTTP interface.

**Base path:** `/api/v1/orders`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/orders` | Place a new order |
| `GET` | `/api/v1/orders/{orderId}` | Retrieve order by ID |
| `GET` | `/api/v1/orders/customer/{customerId}` | Retrieve all orders for a customer |

**Request body — POST /api/v1/orders:**

```json
{
  "customerId": "uuid",
  "items": [
    {
      "productId": "uuid",
      "quantity": 3,
      "pricePerItem": 20.00
    },
    {
      "productId": "uuid",
      "quantity": 7,
      "pricePerItem": 30.00
    }
  ],
  "totalPrice": 270.00
}
```

> **Validation:** The server asserts `totalPrice = ∑(pricePerItem × quantity)`. Returns HTTP 400 on mismatch.

---

## Kafka Topics

| Topic | Producer | Consumers |
|---|---|---|
| `order-placed` | `order-service` | `payment-service`, `analytics-service` |
| `payment-processed` | `payment-service` | `inventory-service`, `notification-service` |
| `order-failed` | `payment-service` | `notification-service` |
| `inventory-reserved` | `inventory-service` | `notification-service`, `analytics-service` |

**Naming convention:** kebab-case throughout.

---

## Database Schemas

### order-service (`orders_db`)

```sql
TABLE orders
  id           UUID PRIMARY KEY
  customer_id  UUID NOT NULL
  total_price  NUMERIC NOT NULL
  status       VARCHAR NOT NULL  -- PLACED | PAYMENT_PROCESSING | PAID | INVENTORY_RESERVED | FAILED
  created_at   TIMESTAMP NOT NULL DEFAULT now()

TABLE order_items
  id             UUID PRIMARY KEY
  order_id       UUID NOT NULL REFERENCES orders(id)
  product_id     UUID NOT NULL
  quantity       INT NOT NULL
  price_per_item NUMERIC NOT NULL
```

### payment-service (`payments_db`)

```sql
TABLE payments
  id            UUID PRIMARY KEY
  order_id      UUID NOT NULL UNIQUE
  status        VARCHAR NOT NULL  -- SUCCESS | FAILED
  processed_at  TIMESTAMP NOT NULL DEFAULT now()
```

Redis stores idempotency keys to prevent duplicate payment processing on Kafka consumer redelivery.

### inventory-service (`inventory_db`)

```sql
TABLE inventory
  id                 UUID PRIMARY KEY
  product_id         UUID NOT NULL UNIQUE
  quantity_available INT NOT NULL

TABLE inventory_reservations
  id                UUID PRIMARY KEY
  order_id          UUID NOT NULL UNIQUE
  product_id        UUID NOT NULL
  quantity_reserved INT NOT NULL
  status            VARCHAR NOT NULL  -- RESERVED | FAILED
  reserved_at       TIMESTAMP NOT NULL DEFAULT now()
```

### notification-service

No database. All notifications are mock (logged to stdout).

### analytics-service

No relational database. State maintained in Kafka Streams state stores (RocksDB-backed). Aggregations tracked:

- **Orders per minute** — windowed count over `order-placed`
- **Revenue totals** — running sum of `totalPrice` from `order-placed`
- **Top products by volume** — count per `productId` from `inventory-reserved`

---

## Build Roadmap

| # | Step | Status |
|---|---|---|
| 1 | Domain model + event schema design | ✅ Complete |
| 2 | Project structure + Gradle multi-module setup | ✅ Complete |
| 3 | Docker Compose baseline — Kafka, PostgreSQL, Redis | ✅ Complete |
| 4 | `order-service` — REST API, validation, persistence, producer | 🔲 Next |
| 5 | `payment-service` — consumer, idempotency, producer | 🔲 Pending |
| 6 | `inventory-service` — consumer, reservation, producer | 🔲 Pending |
| 7 | `notification-service` — two listeners, mock dispatch | 🔲 Pending |
| 8 | `analytics-service` — Kafka Streams topology, aggregations | 🔲 Pending |
| 9 | Observability — OpenTelemetry, Grafana dashboards | 🔲 Pending |
| 10 | Kubernetes + Helm — Minikube manifests, Helm chart per service | 🔲 Pending |
| 11 | GitLab CI — build, test, package, deploy pipelines | 🔲 Pending |