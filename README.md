# Order Processing System

A portfolio-grade, real-time order processing platform demonstrating production-grade backend engineering across microservices, event-driven architecture, security hardening, containerisation, CI/CD, and observability.

---

## Architecture

Only `order-service` exposes a public REST API. All other services are purely event-driven with no inbound HTTP interface. Services communicate exclusively via Kafka events wrapped in a typed `EventEnvelope<T>`, relayed from PostgreSQL outbox tables via Debezium CDC.

```
Client → order-service (REST)
              ↓ outbox → Debezium → order-placed
         payment-service
              ↓ outbox → Debezium → payment-processed      ↓ outbox → Debezium → order-failed
         notification-service                           notification-service
         analytics-service (Kafka Streams)
```

---

## Services

| Service | Responsibility |
|---|---|
| `order-service` | Accepts orders via REST, persists order, writes `order-placed` outbox event |
| `payment-service` | Consumes `order-placed`, simulates payment with retry logic, writes `payment-processed` or `order-failed` outbox events |
| `notification-service` | Consumes terminal events, sends mock notifications |
| `analytics-service` | Kafka Streams topology — orders per minute, top products, payment outcomes, confirmed revenue |
| `inventory-service` | Deferred — standalone follow-on project |

---

## Tech Stack

| Concern | Technology |
|---|---|
| Language | Kotlin 2.4.0 (JVM 21) |
| Framework | Spring Boot 4.1.0 |
| Build | Gradle 9.x (Kotlin DSL), multi-module monorepo |
| Messaging | Apache Kafka 4.0 (KRaft mode), Kafka Streams |
| CDC / Outbox Relay | Kafka Connect + Debezium PostgreSQL connector |
| Serialization | Custom Jackson-based `EventSerializer` / `EventDeserializer` (shared module) |
| Database | PostgreSQL 17 (one logical DB per service) |
| Cache / Idempotency | Redis (`payment-service`) |
| Security | SASL/SCRAM-SHA-512 on all Kafka listeners |
| API Docs | springdoc-openapi (Swagger UI) |
| Containerisation | Docker, Docker Compose |
| Orchestration | Minikube + Helm (planned) |
| Observability | OpenTelemetry, Prometheus, Grafana, Loki, Tempo (planned) |
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
│   └── order-load-test.js
├── infra/
│   ├── docker-compose.yml
│   ├── .env                        # gitignored — real credentials
│   ├── .env.example                # committed — placeholder values
│   ├── kafka/
│   │   ├── kafka_server_jaas.conf  # gitignored — broker SASL config
│   │   └── healthcheck-client.properties
│   ├── kafka-connect/
│   │   ├── Dockerfile
│   │   ├── register-connectors.sh
│   │   ├── init-topics.sh
│   │   └── topics.conf
│   └── postgres/
│       └── init.sql
├── .dockerignore
├── .gitignore
├── .github/workflows/ci.yaml
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
└── gradle/wrapper/gradle-wrapper.properties
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

**Naming convention:** kebab-case throughout.

---

## Event Flow

1. Client sends `POST /api/v1/orders` to `order-service`
2. `order-service` persists the order and writes an `order-placed` outbox event in a single transaction
3. Debezium reads the outbox row from the PostgreSQL WAL and publishes it to `order-placed`
4. `payment-service` consumes `order-placed`:
   - Success → writes `payment-processed` outbox event
   - Failure → writes `payment-retry` outbox event (up to `max-attempts`), then `order-failed` on exhaustion
5. Debezium relays outbox events to their respective Kafka topics
6. `notification-service` operates independent listeners per terminal event
7. `analytics-service` consumes all events for real-time aggregations via Kafka Streams

---

## REST API — order-service

Base path: `/api/v1/orders`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/orders` | Place a new order |

Swagger UI: `http://localhost:8080/swagger-ui.html`

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

`totalPrice` is calculated server-side.

---

## REST API — analytics-service

Base path: `/api/v1/analytics`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/v1/analytics/orders-per-minute` | Order count in the current 1-minute tumbling window |
| `GET` | `/api/v1/analytics/top-products` | Running order count per product ID |
| `GET` | `/api/v1/analytics/payment-outcomes` | Running count of SUCCESS and FAILED payment outcomes |
| `GET` | `/api/v1/analytics/confirmed-revenue` | Running total revenue from confirmed payments |

Available at `http://localhost:8084` via Docker Compose.

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

`RETRYING` is in-flight. `SUCCESS` and `FAILED` are terminal. Redis stores idempotency keys (`idempotency:payment:<orderId>`) to prevent duplicate processing on consumer redelivery.

### notification-service
No database. All notifications are mock (logged to stdout).

### analytics-service
No relational database. State is maintained in Kafka Streams state stores (RocksDB-backed). Four named stores: `orders-per-minute` (window store), `top-products`, `payment-outcomes`, `confirmed-revenue` (key-value stores).

---

## Local Development

### Prerequisites

- Docker Engine 27+ and Docker Compose v2+
- JDK 21
- Copy the environment file and fill in credentials:

```bash
cp infra/.env.example infra/.env
```

The `.env.example` contains all required keys. The defaults work for local development without changes.

### Start Everything

```bash
cd infra
docker compose up --build -d
```

This starts Kafka (KRaft, SASL/SCRAM-SHA-512), PostgreSQL, Redis, Kafka Connect, topic init, connector registration, and all four services. All secrets and SCRAM credentials are bootstrapped automatically — no manual setup required.

To tear down:

```bash
docker compose down        # keep volumes
docker compose down -v     # wipe volumes (fresh start)
```

### Run a Service Locally Against Containerised Infra

Start only infrastructure:

```bash
cd infra
docker compose up kafka postgres redis kafka-connect-1 kafka-topic-init connect-init -d
```

Then run a service:

```bash
./gradlew :order-service:bootRun --no-daemon
./gradlew :payment-service:bootRun --no-daemon
./gradlew :notification-service:bootRun --no-daemon
./gradlew :analytics-service:bootRun --no-daemon
```

### Spring Profiles

| Profile | Used when |
|---|---|
| `local` | Running via `bootRun` against containerised infra |
| `docker` | Running inside Docker Compose |
| `k8s` | Running in Kubernetes |
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

**Check Kafka consumer group lag** (requires SASL credentials):
```bash
docker exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --command-config /tmp/client.properties \
  --describe --group payment-service

docker exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --command-config /tmp/client.properties \
  --describe --group analytics-service
```

**Consume from a topic:**
```bash
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --consumer.config /tmp/client.properties \
  --topic order-placed \
  --from-beginning \
  --max-messages 1
```

---

## Security

All Kafka communication is secured with SASL/SCRAM-SHA-512. Unauthenticated connections are rejected.

**Two SCRAM users are provisioned automatically on startup:**

| User | Used by |
|---|---|
| `kafka-admin` | Broker inter-broker auth, topic init, SCRAM bootstrapping |
| `kafka-client` | All Spring services, Kafka Connect |

**Bootstrap flow:**

1. Kafka starts with a JAAS file pre-authorising `kafka-admin` at the JVM level for inter-broker auth
2. A `PLAINTEXT_INTERNAL` listener on port 9094 (Docker-internal only, never exposed to host) allows the `kafka-scram-init` container to write SCRAM credentials into Kafka's metadata store without a circular auth dependency
3. Once credentials exist, all clients connect on port 9092 with full SASL_PLAINTEXT auth
4. `kafka-scram-init` exits, topics are created, connectors are registered

Credentials are stored in `infra/.env` (gitignored). See `infra/.env.example` for required keys.

---

## Load Testing

```bash
k6 run load-test/order-load-test.js
```

Monitor consumer lag in a second terminal:

```bash
watch -n 5 'docker exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --command-config /tmp/client.properties \
  --describe --group payment-service'
```

### Observed Results (local Docker Compose, single host)

| Metric | Value |
|---|---|
| Order throughput | ~270 req/s sustained |
| p95 HTTP latency | 18ms |
| p99 HTTP latency | ~25ms |
| Error rate | 0% across 89,000+ requests |
| Peak consumer lag (payment-service) | ~42,000 events at 500 VUs |
| Debezium connector stability | RUNNING throughout |

**Bottleneck:** `payment-service` consumer lag grows linearly above ~100 concurrent users due to `Thread.sleep` in the retry path. The production fix is a delay topic pattern — documented under Known Simplifications.

---

## Analytics Service — Kafka Streams Design

`analytics-service` uses a `String/String` default serde strategy. All deserialization to typed objects happens inside `mapValues` — typed objects never touch state stores or repartition topics, avoiding serde mismatches.

**Topology pipelines:**

- `orders-per-minute` — `order-placed` → `groupByKey()` → `windowedBy(1 minute)` → `count()` → window store
- `top-products` — `order-placed` → `flatMap()` (fan out per line item) → `groupByKey()` → `count()` → key-value store
- `payment-outcomes` — `payment-processed` + `order-failed` → `merge()` → `groupByKey()` → `count()` → key-value store
- `confirmed-revenue` — `payment-processed` → extract `totalPrice` → `selectKey("total")` → `aggregate()` → key-value store

The runtime image uses `eclipse-temurin:21-jre-jammy` (not Alpine) because RocksDB requires `libstdc++.so.6`, which is absent in musl-based images.

---

## Code Quality

```bash
./gradlew spotlessApply   # auto-format
./gradlew spotlessCheck   # CI check
```

JaCoCo enforces minimum coverage thresholds on all modules:

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

| Stage | Jobs | Trigger |
|---|---|---|
| Validate | Spotless formatting check | All branches |
| Test | Unit + integration tests (parallel) | All branches |
| Coverage | JaCoCo verification, reports as artifacts | All branches |
| Build | Build and push Docker images to GHCR | `main` only |
| Deploy | Minikube + Helm (placeholder) | `main` only |

---

## Schema Evolution

`EventEnvelope` carries `schemaVersion: Int = 1`. Compatibility policy:

- New optional fields with defaults → backward compatible, no version bump
- Field removal or type changes → version bump + documented migration
- Consumers ignore unknown fields (`FAIL_ON_UNKNOWN_PROPERTIES = false`)

---

## Key Design Decisions

| Decision | Rationale |
|---|---|
| Transactional Outbox Pattern | Order record and outbox row written in one DB transaction. Eliminates dual-write problem — Kafka publish failures cannot cause data inconsistency. |
| SASL/SCRAM-SHA-512 | PLAIN sends passwords in cleartext. SCRAM uses salted challenge-response — passwords are never transmitted directly. Credentials stored in Kafka's own metadata store. |
| PLAINTEXT_INTERNAL bootstrap listener | SCRAM credentials must be written after the broker starts, but writing them requires auth. A Docker-internal plaintext listener breaks the circular dependency without exposing an unauthenticated port to the host. |
| Kafka Connect distributed mode | Single worker locally, scales to multiple workers in Kubernetes. No migration needed when moving to production. |
| Debezium `pgoutput` plugin | Built into PostgreSQL 10+. No additional installation or `wal2json` dependency. |
| Debezium EventRouter SMT | Routes outbox events to Kafka topics by reading `aggregatetype` from the outbox row. The service writes `aggregatetype = "order-placed"` and the event lands on the correct topic automatically. |
| Environment variable injection for connector credentials | Connector registration script injects Postgres credentials directly from environment variables via heredoc. Removes dependency on a static secrets file that Docker can accidentally create as a directory. |
| Dead Letter Topics | `DefaultErrorHandler` with `FixedBackOff` and `DeadLetterPublishingRecoverer` on all consumer factories. Failed messages routed to `.DLT` topics after retry exhaustion. |
| Custom Jackson serialization | Confluent Schema Registry library is in maintenance mode. Custom `EventSerializer`/`EventDeserializer` in shared module is simpler, dependency-free, and fully owned. |
| `schemaVersion` on `EventEnvelope` | Lightweight schema evolution without Avro or Schema Registry overhead. |
| JSONB for line items | Line items are never queried independently. JSONB avoids a join and scales cleanly. |
| Server-side `totalPrice` | Client-submitted prices are never trusted for money calculations. |
| Explicit `order-failed` event | Avoids ambiguous consumer contracts from a `payment-processed` event carrying a `FAILED` status field. |
| Flyway owns the schema | `hibernate.ddl-auto=validate`. Hibernate validates, never modifies. |
| `totalPrice` on `PaymentProcessed` | `analytics-service` needs confirmed payment amount for revenue calculation. Self-contained event eliminates a stream-stream join with `order-placed`. |
| `String/String` serdes in Kafka Streams | State stores and repartition topics use String values. Deserialization to typed objects happens in memory only inside `mapValues`. Eliminates serde mismatches entirely. |
| Three-stage Dockerfiles | Builder (gradle:jdk21-alpine) → extractor (eclipse-temurin:21-jdk-alpine) → runtime (eclipse-temurin:21-jre-alpine). Layer caching separates dependency resolution from application code. |

---

## Known Simplifications

| Simplification | Production Alternative |
|---|---|
| Single PostgreSQL container (local) | Isolated instances per service |
| `Thread.sleep` in payment retry path | Delay topic pattern — publish to a `payment-retry-delayed` topic with a target timestamp, consume only when delay has elapsed. Removes blocking sleep from consumer threads entirely. |
| `notification-service` logs to stdout | Structured logging via SLF4J with OpenTelemetry log correlation |
| No application-level authentication | JWT via Spring Authorization Server (Phase 5 — planned) |
| Single Kafka partition per topic | Multiple partitions + multiple service instances for horizontal scaling |
| Testcontainers reuse not enabled | Enable reuse to reduce container startup cost on repeated local test runs |
| Blocking threading model | Kotlin coroutines — full-stack async architectural commitment |
| OutboxCleanupJob scheduled deletion | DB partitioning by `createdat` at scale |

---

## Build Sequence

- [x] Domain model + event schema design
- [x] Project structure + Gradle multi-module setup
- [x] Docker Compose baseline (Kafka, PostgreSQL, Redis)
- [x] `order-service` — REST API, validation, persistence, outbox producer
- [x] `order-service` — unit tests, integration tests, JaCoCo coverage
- [x] `order-service` — multi-stage Dockerfile, Docker Compose, CI
- [x] Code quality — Spotless + ktlint
- [x] GitHub Actions CI pipeline
- [x] `payment-service` — consumer, idempotency, retry, outbox producers
- [x] `payment-service` — unit tests, integration tests, JaCoCo coverage
- [x] `payment-service` — Dockerfile, Docker Compose, CI
- [x] Idiomatic Kotlin refactor
- [x] `integrationTest` Gradle task split
- [x] `notification-service` — consumers, mock notifications, tests, Dockerfile, Docker Compose, CI
- [x] `inventory-service` deferred
- [x] Reliability pass — outbox pattern, Debezium CDC, Kafka Connect, DLTs, schema versioning
- [x] `analytics-service` — Kafka Streams topology, state stores, REST query endpoints, tests, Dockerfile, Docker Compose, CI
- [x] Load testing — k6 script with analytics poller and consumer lag tracking
- [x] Security hardening — SASL/SCRAM-SHA-512 on all Kafka listeners, per-service credentials, automated bootstrap
- [x] Dockerfile improvements — gradle:jdk21-alpine builder, layered extraction, fixed ENTRYPOINT
- [ ] Payment retry delay topic — replace `Thread.sleep` with delay topic pattern
- [ ] Phase 5 — Spring Authorization Server (`auth-service`, JWT issuance, `order-service` as resource server)
- [ ] Phase 6 — Domain rewire (typed error codes, X-Correlation-Id, structured logging, new order query endpoints)
- [ ] Phase 7 — Analytics enrichment (typed responses, AOV calculation, `/summary` endpoint)
- [ ] Observability — OpenTelemetry, Prometheus, Grafana, Loki, Tempo
- [ ] Kubernetes + Helm — Minikube manifests, Helm chart per service