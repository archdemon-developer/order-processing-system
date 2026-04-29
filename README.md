# Order Processing System

A portfolio-grade, real-time order processing platform built with a microservices architecture and event-driven communication via Apache Kafka. The system demonstrates production-grade backend engineering practices across microservices, event-driven design, containerisation, Kubernetes, CI/CD, and observability.

---

## Architecture Overview

Only `order-service` exposes a public REST API. All other services are purely event-driven with no inbound HTTP interface. Services communicate exclusively via Kafka events wrapped in a typed `EventEnvelope<T>`.

```
Client → order-service (REST)
           ↓ order-placed (via outbox)
       payment-service
           ↓ payment-processed (via outbox)       ↓ order-failed (via outbox)
       notification-service                    notification-service
       analytics-service
```

`inventory-service` is deferred to a standalone follow-on project. It will integrate with this system when built.

---

## Services

| Service | Responsibility |
|---|---|
| `order-service` | Accepts orders via REST, writes `order-placed` outbox event |
| `payment-service` | Consumes `order-placed`, simulates payment with retry, writes `payment-processed` or `order-failed` outbox events |
| `notification-service` | Consumes `payment-processed` and `order-failed`, sends mock notifications |
| `analytics-service` | Kafka Streams consumer, real-time aggregations — orders per minute, top products, payment outcomes, confirmed revenue |
| `inventory-service` | Deferred — standalone follow-on project |

---

## Tech Stack

| Concern | Technology |
|---|---|
| Language | Kotlin 2.x (JVM 21) |
| Framework | Spring Boot 4.x |
| Build | Gradle (Kotlin DSL), multi-module monorepo |
| Messaging | Apache Kafka (KRaft mode), Kafka Streams |
| CDC / Outbox Relay | Kafka Connect + Debezium PostgreSQL connector |
| Serialization | Custom Jackson-based `EventSerializer` / `EventDeserializer` (shared module) |
| Database | PostgreSQL 17 (one logical DB per service) |
| Cache / Idempotency | Redis (`payment-service`) |
| API Docs | springdoc-openapi 3.x (Swagger UI) |
| Containerisation | Docker, Docker Compose |
| Orchestration | Minikube + Helm |
| Observability | OpenTelemetry, Prometheus, Grafana, Loki, Tempo |
| CI/CD | GitHub Actions |
| Code Quality | Spotless + ktlint |
| Load Testing | k6 |

---

## Repository Structure

```
order-processing-system/
├── shared/                         # Shared event types, envelope, serialization, outbox
│   └── src/main/kotlin/com/orderprocessing/shared/
│       ├── envelope/EventEnvelope.kt
│       ├── events/
│       │   ├── OrderPlaced.kt
│       │   ├── PaymentProcessed.kt
│       │   ├── PaymentRetry.kt
│       │   ├── InventoryReserved.kt
│       │   └── OrderFailed.kt
│       ├── model/OrderItem.kt
│       ├── outbox/
│       │   ├── OutboxEvent.kt
│       │   └── OutboxEventRepository.kt
│       └── serialization/
│           ├── EventSerializer.kt
│           └── EventDeserializer.kt
├── order-service/
│   ├── Dockerfile
│   └── src/
├── payment-service/
│   ├── Dockerfile
│   └── src/
├── notification-service/
│   ├── Dockerfile
│   └── src/
├── analytics-service/
│   ├── Dockerfile
│   └── src/
├── inventory-service/              # Deferred — standalone follow-on project
├── load-test/
│   └── order-load-test.js          # k6 load test script
├── infra/
│   ├── docker-compose.yml
│   ├── kafka-connect/
│   │   ├── Dockerfile
│   │   ├── register-connectors.sh
│   │   ├── init-topics.sh
│   │   ├── topics.conf
│   │   └── connect-secrets.properties  # gitignored — local dev only
│   └── postgres/init.sql
├── .dockerignore
├── .github/workflows/ci.yaml
├── build.gradle.kts
└── settings.gradle.kts
```

---

## Kafka Topics

| Topic | Producer | Consumers | Retention |
|---|---|---|---|
| `order-placed` | Debezium (outbox) | `payment-service`, `analytics-service` | 7 days |
| `payment-retry` | Debezium (outbox) | `payment-service` | 1 day |
| `payment-processed` | Debezium (outbox) | `notification-service`, `analytics-service` | 7 days |
| `order-failed` | Debezium (outbox) | `notification-service`, `analytics-service` | 7 days |
| `order-placed.DLT` | Spring Kafka error handler | — | 30 days |
| `payment-retry.DLT` | Spring Kafka error handler | — | 30 days |
| `payment-processed.DLT` | Spring Kafka error handler | — | 30 days |
| `order-failed.DLT` | Spring Kafka error handler | — | 30 days |
| `inventory-reserved` | `inventory-service` (deferred) | `notification-service`, `analytics-service` | — |

**Naming convention:** kebab-case throughout.

---

## Event Flow

1. Client sends `POST /api/v1/orders` to `order-service`
2. `order-service` persists the order and writes an `order-placed` outbox event — both in one transaction
3. Debezium reads the outbox row from the WAL and publishes it to the `order-placed` Kafka topic
4. `payment-service` consumes `order-placed`:
   - Success → writes `payment-processed` outbox event
   - Failure → writes `payment-retry` outbox event (up to `max-attempts`), then `order-failed` on exhaustion
5. Debezium relays outbox events to their respective Kafka topics
6. `notification-service` operates independent listeners per terminal event:
   - `payment-processed` → "Your payment was successful."
   - `order-failed` → "Your order could not be processed."
7. `analytics-service` consumes `order-placed`, `payment-processed`, and `order-failed` for real-time aggregations via Kafka Streams

---

## REST API — order-service

Base path: `/api/v1/orders`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/orders` | Place a new order |

Swagger UI available at `http://localhost:8080/swagger-ui.html` when running locally.

### Request Body — POST /api/v1/orders

```json
{
   "customerId": "uuid",
   "items": [
      {
         "productId": "uuid",
         "quantity": 3,
         "pricePerItem": 20.00
      }
   ]
}
```

`totalPrice` is calculated server-side. Clients do not submit it.

---

## REST API — analytics-service

Base path: `/api/v1/analytics`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/analytics/orders-per-minute` | Count of orders placed in the current 1-minute tumbling window |
| `GET` | `/api/v1/analytics/top-products` | Running count of orders per product ID |
| `GET` | `/api/v1/analytics/payment-outcomes` | Running count of SUCCESS and FAILED payment outcomes |
| `GET` | `/api/v1/analytics/confirmed-revenue` | Running total revenue from confirmed payments |

Available at `http://localhost:8084` when running locally via Docker Compose.

---

## Database Schemas

### order-service (`orders_db`)

```sql
TABLE orders
  id            UUID           PRIMARY KEY
  customer_id   UUID           NOT NULL
  status        VARCHAR(50)    NOT NULL DEFAULT 'PENDING'
  items         JSONB          NOT NULL
  total_price   NUMERIC(19,4)  NOT NULL
  created_at    TIMESTAMPTZ    NOT NULL DEFAULT now()

TABLE outbox_events
  id             UUID        PRIMARY KEY
  aggregatetype  VARCHAR     NOT NULL
  aggregateid    VARCHAR     NOT NULL
  type           VARCHAR     NOT NULL
  payload        JSONB       NOT NULL
  createdat      TIMESTAMPTZ NOT NULL DEFAULT now()
```

### payment-service (`payments_db`)

```sql
TABLE payments
  id             UUID           PRIMARY KEY
  order_id       UUID           NOT NULL UNIQUE
  transaction_id UUID           UNIQUE
  customer_id    UUID           NOT NULL
  status         VARCHAR        NOT NULL  -- RETRYING | SUCCESS | FAILED
  attempts       INT            NOT NULL DEFAULT 1
  total_price    NUMERIC(19,4)  NOT NULL
  processed_at   TIMESTAMP      NOT NULL DEFAULT now()

TABLE outbox_events
  id             UUID        PRIMARY KEY
  aggregatetype  VARCHAR     NOT NULL
  aggregateid    VARCHAR     NOT NULL
  type           VARCHAR     NOT NULL
  payload        JSONB       NOT NULL
  createdat      TIMESTAMPTZ NOT NULL DEFAULT now()
```

`RETRYING` is an in-flight status. `SUCCESS` and `FAILED` are terminal states. Redis stores idempotency keys (`idempotency:payment:<orderId>`) to prevent duplicate processing on Kafka consumer redelivery. The idempotency check only short-circuits on terminal states.

### notification-service
No database. All notifications are mock (logged to stdout via `println`).

### analytics-service
No relational database. State maintained in Kafka Streams state stores (RocksDB-backed). Four named stores: `orders-per-minute` (window store), `top-products`, `payment-outcomes`, `confirmed-revenue` (key-value stores).

---

## Local Development

### Prerequisites
- Docker Engine 27+ and Docker Compose v2+
- JDK 21
- Gradle (or use the wrapper `./gradlew`)

### Secrets Setup

Before starting, create the Connect secrets file (gitignored):

```bash
cp infra/kafka-connect/connect-secrets.properties.example infra/kafka-connect/connect-secrets.properties
```

The default credentials match the local PostgreSQL setup and require no changes for local development. In production, replace `FileConfigProvider` with HashiCorp Vault or Kubernetes secrets.

### Start Everything (Recommended)

```bash
docker compose -f infra/docker-compose.yml up --build -d
```

This starts:
- Kafka (KRaft mode) — ports `9092` (container-to-container), `29092` (host access)
- PostgreSQL 17 — port `5432` — creates `orders_db`, `payments_db`
- Redis — port `6379`
- Kafka Connect (distributed mode) — port `8083`
- `kafka-topic-init` — creates all topics with retention policies and DLTs, then exits
- `connect-init` — registers Debezium connectors once Connect is healthy, then exits
- `order-service` — port `8080`
- `payment-service` — no exposed port (Kafka consumer only)
- `notification-service` — no exposed port (Kafka consumer only)
- `analytics-service` — port `8084`

To bring everything down:

```bash
docker compose -f infra/docker-compose.yml down
```

To also wipe volumes:

```bash
docker compose -f infra/docker-compose.yml down -v
```

### Run a Service Locally Against Containerised Infra

Start only infrastructure:

```bash
docker compose -f infra/docker-compose.yml up kafka postgres redis kafka-connect-1 kafka-topic-init connect-init -d
```

Then run the service:

```bash
./gradlew :order-service:bootRun --no-daemon
./gradlew :payment-service:bootRun --no-daemon
./gradlew :notification-service:bootRun --no-daemon
./gradlew :analytics-service:bootRun --no-daemon
```

### Spring Profiles

| Profile | Used when |
|---|---|
| `local` | Running service via `bootRun` against containerised infra |
| `docker` | Running service inside Docker Compose |
| `k8s` | Running in Kubernetes (Minikube/Helm) |
| `test` | Integration tests via Testcontainers |

### Operations

**Check connector status:**
```bash
curl http://localhost:8083/connectors/debezium-orders-outbox/status
curl http://localhost:8083/connectors/debezium-payments-outbox/status
```

**Restart a failed connector:**
```bash
curl -X POST http://localhost:8083/connectors/debezium-orders-outbox/restart
curl -X POST http://localhost:8083/connectors/debezium-payments-outbox/restart
```

**Check Kafka consumer group lag:**
```bash
docker exec kafka /bin/kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group payment-service
docker exec kafka /bin/kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group analytics-service
```

---

## Load Testing

A k6 load test script is included at `load-test/order-load-test.js`. It runs two concurrent scenarios — an order load scenario and an analytics poller — giving real-time visibility into consumer lag and revenue accumulation during the test.

### Install k6

```bash
sudo apt-get install -y ca-certificates gnupg
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://dl.k6.io/key.gpg | sudo gpg --dearmor -o /etc/apt/keyrings/k6-archive-keyring.gpg
echo "deb [signed-by=/etc/apt/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update
sudo apt-get install k6
```

### Run the load test

```bash
k6 run load-test/order-load-test.js
```

In a second terminal, monitor Kafka consumer lag:

```bash
watch -n 5 'docker exec kafka /bin/kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group payment-service'
```

### Observed Results (local Docker Compose, single host)

| Metric | Value |
|---|---|
| Order throughput | ~270 req/s sustained |
| p95 HTTP latency | 18ms |
| p99 HTTP latency | ~25ms |
| Error rate | 0% across 89,000+ requests |
| Peak consumer lag (payment-service) | ~42,000 events at 500 VUs |
| Debezium connector stability | RUNNING throughout entire test |

**Bottleneck analysis:** `payment-service` consumer lag grows linearly above ~100 concurrent users. The root cause is `Thread.sleep` in the retry path — each failed payment blocks a consumer thread for the retry delay duration. At 500 VUs with a 30% failure rate, a significant fraction of consumer threads are sleeping simultaneously. The production fix is a delay topic pattern (publish to a delayed retry topic with a target timestamp, consume only when the delay has elapsed), which removes the blocking sleep entirely. This is documented as a known simplification below.

---

## Schema Evolution

`EventEnvelope` carries a `schemaVersion: Int = 1` field. The compatibility policy is:

- New optional fields with defaults are backward compatible — no version bump required
- Field removal or type changes require a version bump and a documented migration path
- Consumers ignore unknown fields (`FAIL_ON_UNKNOWN_PROPERTIES = false`)

---

## Analytics Service — Kafka Streams Design

`analytics-service` uses Kafka Streams with a `String/String` default serde strategy. All source topics carry JSON strings. Deserialization to typed objects happens inside `mapValues` — typed objects never touch state stores or repartition topics, avoiding serde mismatches entirely.

**Topology pipelines:**

- `orders-per-minute` — `order-placed` → `groupByKey()` → `windowedBy(1 minute)` → `count()` → window store
- `top-products` — `order-placed` → `flatMap()` (fan out per line item) → `groupByKey()` → `count()` → key-value store
- `payment-outcomes` — `payment-processed` + `order-failed` → map to status string → `merge()` → `groupByKey()` → `count()` → key-value store
- `confirmed-revenue` — `payment-processed` → extract `totalPrice` → `selectKey("total")` → `aggregate()` (running BigDecimal sum) → key-value store

No stream-stream joins are used. Each event carries enough data to determine its outcome independently.

**Note:** RocksDB (the default Kafka Streams state store backend) requires `libstdc++.so.6`. The `analytics-service` Dockerfile uses `eclipse-temurin:21-jre-jammy` (Ubuntu-based) as the runtime image rather than Alpine, which does not include this library.

---

## Code Quality

### Formatting

```bash
./gradlew spotlessApply   # auto-format
./gradlew spotlessCheck   # CI check
```

### Test Coverage

JaCoCo enforces minimum coverage thresholds:

| Counter | Threshold |
|---|---|
| Instruction | 90% |
| Branch | 80% |

```bash
./gradlew :order-service:test :order-service:integrationTest --no-daemon
./gradlew :payment-service:test :payment-service:integrationTest --no-daemon
./gradlew :notification-service:test :notification-service:integrationTest --no-daemon
./gradlew :analytics-service:test :analytics-service:integrationTest --no-daemon
```

---

## CI/CD Pipeline

| Stage | Jobs | Runs on |
|---|---|---|
| `validate` | Spotless formatting check | All branches |
| `test` | Unit + integration tests (parallel) | All branches |
| `coverage` | JaCoCo verification, reports as artifacts | All branches |
| `build` | Build and push Docker images for all services | `main` only |
| `deploy` | Placeholder — Minikube/Helm (coming soon) | `main` only |

---

## Observability

All services emit traces, metrics, and structured logs via OpenTelemetry:
- Traces → Tempo
- Metrics → Prometheus → Grafana
- Logs → Loki

---

## Key Design Decisions

| Decision | Rationale |
|---|---|
| Transactional Outbox Pattern | Service methods write domain records and outbox rows in a single DB transaction. Debezium reads the PostgreSQL WAL and relays outbox rows to Kafka. This eliminates the dual-write problem — Kafka publish failures can no longer cause data inconsistency. |
| Kafka Connect distributed mode | Single worker locally, scales to multiple workers in Kubernetes. Distributed mode is configured from the start — no migration needed when moving to production. |
| Debezium `pgoutput` plugin | Built into PostgreSQL 10+. No additional installation required. No dependency on `wal2json`. |
| Debezium EventRouter SMT | Routes outbox events to Kafka topics by reading `aggregatetype` from the outbox row. The service writes `aggregatetype = "order-placed"` and the event lands on the `order-placed` topic automatically. |
| `FileConfigProvider` for secrets | Credentials are stored in a gitignored properties file and injected via Connect's `FileConfigProvider`. They never appear in connector JSON configs. Production replaces this with HashiCorp Vault or Kubernetes secrets. |
| Dead Letter Topics | `DefaultErrorHandler` with `FixedBackOff` and `DeadLetterPublishingRecoverer` on all consumer container factories. Failed messages are routed to `.DLT` topics with 30-day retention after exhausting retries. |
| `schemaVersion` on `EventEnvelope` | Lightweight schema evolution without Avro or Schema Registry. Default value of `1` means all existing code compiles without changes. |
| Outbox retention cleanup | `OutboxCleanupJob` runs on a configurable schedule in `order-service` and `payment-service`, deleting rows older than `outbox.retention-hours`. Default 24 hours. |
| Jackson serialization (custom) | Confluent Schema Registry library is in maintenance mode. Custom `EventSerializer`/`EventDeserializer` in shared module is simpler, dependency-free, and fully under our control. |
| JSONB for line items | Line items are never queried independently. JSONB avoids a join and scales cleanly. |
| Server-side `totalPrice` | Never trust the client for money. |
| Explicit `order-failed` event | Avoids ambiguous consumer contracts from a `payment-processed` event with a `FAILED` status field. |
| Flyway owns the schema | `hibernate.ddl-auto=validate`. Hibernate validates, never modifies. |
| KRaft mode | Confluent Platform 8.0 removed ZooKeeper. |
| Idiomatic Kotlin throughout | Nullable types, `isTerminal` enum properties, Kotlin `Duration` extensions, `?: false` guards. Java idioms only at unavoidable interop boundaries. |
| `totalPrice` on `PaymentProcessed` | `analytics-service` needs the confirmed payment amount to compute revenue. Carrying `totalPrice` on `PaymentProcessed` makes each event self-contained — no stream-stream join with `order-placed` required. |
| `String/String` serdes in Kafka Streams | All Kafka Streams state stores and repartition topics use `String` values. Deserialization to typed objects happens inside `mapValues` in memory only. This avoids serde mismatches entirely and keeps the topology simple. |
| `eclipse-temurin:21-jre-jammy` for analytics-service | RocksDB requires `libstdc++.so.6` which is not present in Alpine. The analytics-service runtime image uses Ubuntu Jammy to satisfy this native dependency. |

---

## Known Simplifications

| Simplification | Production Alternative |
|---|---|
| Single PostgreSQL container (local) | Isolated instances per service |
| `FileConfigProvider` for secrets | HashiCorp Vault provider or Kubernetes secrets |
| `OutboxCleanupJob` scheduled deletion | DB partitioning by `createdat` at scale |
| Connect distributed mode on single host | Multi-host Connect cluster in production |
| Payment is a simulation | Stripe / Adyen sandbox integration |
| `notification-service` logs to stdout | Structured logging via SLF4J with OpenTelemetry log correlation |
| No authentication / authorisation | JWT via Spring Security |
| Testcontainers reuse not enabled | Enable reuse to reduce container startup cost on repeated local runs |
| Blocking threading model | Kotlin coroutines — full-stack async architectural commitment |
| `Thread.sleep` in payment retry path | Delay topic pattern — publish to a `payment-retry-delayed` topic with a target timestamp, consume only when delay has elapsed. Removes blocking sleep from consumer thread entirely, allowing payment-service to process at full throughput. |
| Single Kafka partition per topic | Multiple partitions + multiple service instances for horizontal scaling |

---

## Build Sequence

- [x] Domain model + event schema design
- [x] Project structure + Gradle multi-module setup
- [x] Docker Compose baseline (Kafka, PostgreSQL, Redis)
- [x] `order-service` — REST API, validation, persistence, `order-placed` producer
- [x] `order-service` — unit tests + integration tests, JaCoCo coverage
- [x] `order-service` — multi-stage Dockerfile, added to Docker Compose
- [x] Code quality — Spotless + ktlint
- [x] GitHub Actions CI pipeline
- [x] `payment-service` — consumer, idempotency, retry, producers
- [x] `payment-service` — unit tests + integration tests, JaCoCo coverage
- [x] `payment-service` — Dockerfile, Docker Compose, CI updated
- [x] Idiomatic Kotlin refactor
- [x] `integrationTest` Gradle task split
- [x] `notification-service` — consumers, mock notifications, tests, Dockerfile, Docker Compose, CI updated
- [x] `inventory-service` deferred
- [x] Reliability pass — outbox pattern, Debezium CDC, Kafka Connect, DLTs, schema versioning
- [x] `analytics-service` — Kafka Streams topology, state stores, REST query endpoints, unit + integration tests, Dockerfile, Docker Compose, CI updated
- [x] Load testing — k6 script with analytics poller and consumer lag tracking
- [ ] Payment retry delay topic — replace `Thread.sleep` with delay topic pattern
- [ ] Observability — OpenTelemetry, Grafana dashboards
- [ ] Kubernetes + Helm — Minikube manifests, Helm chart per service