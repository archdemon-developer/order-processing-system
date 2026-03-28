# Order Processing System

A portfolio-grade, real-time order processing platform built with a microservices architecture and event-driven communication via Apache Kafka. The system demonstrates production-grade backend engineering practices across microservices, event-driven design, containerisation, Kubernetes, CI/CD, and observability.

---

## Architecture Overview

Only `order-service` exposes a public REST API. All other services are purely event-driven with no inbound HTTP interface. Services communicate exclusively via Kafka events wrapped in a typed `EventEnvelope<T>`.

```
Client → order-service (REST)
           ↓ order-placed
       payment-service
           ↓ payment-processed         ↓ order-failed
       inventory-service           notification-service
           ↓ inventory-reserved
       notification-service
       analytics-service
```

---

## Services

| Service | Responsibility |
|---|---|
| `order-service` | Accepts orders via REST, publishes `order-placed` event |
| `payment-service` | Consumes `order-placed`, simulates payment, publishes `payment-processed` or `order-failed` |
| `inventory-service` | Consumes `payment-processed`, reserves stock, publishes `inventory-reserved` |
| `notification-service` | Consumes terminal events, sends mock notifications |
| `analytics-service` | Kafka Streams consumer, real-time aggregations |

---

## Tech Stack

| Concern | Technology |
|---|---|
| Language | Kotlin 2.x (JVM 21) |
| Framework | Spring Boot 4.x |
| Build | Gradle (Kotlin DSL), multi-module monorepo |
| Messaging | Apache Kafka (KRaft mode), Kafka Streams |
| Serialization | Custom Jackson-based `EventSerializer` / `EventDeserializer` (shared module) |
| Database | PostgreSQL 17 (one logical DB per service) |
| Cache / Idempotency | Redis (payment-service) |
| API Docs | springdoc-openapi 3.x (Swagger UI) |
| Containerisation | Docker, Docker Compose |
| Orchestration | Minikube + Helm |
| Observability | OpenTelemetry, Prometheus, Grafana, Loki, Tempo |
| CI/CD | GitLab CI |
| Code Quality | Spotless + ktlint |

---

## Repository Structure

```
order-processing-system/
├── shared/                         # Shared event types, envelope, serialization
│   └── src/main/kotlin/com/orderprocessing/shared/
│       ├── envelope/EventEnvelope.kt
│       ├── events/
│       │   ├── OrderPlaced.kt
│       │   ├── PaymentProcessed.kt
│       │   ├── InventoryReserved.kt
│       │   └── OrderFailed.kt
│       ├── model/OrderItem.kt
│       └── serialization/
│           ├── EventSerializer.kt
│           └── EventDeserializer.kt
├── order-service/
│   ├── Dockerfile
│   └── src/
├── payment-service/
├── inventory-service/
├── notification-service/
├── analytics-service/
├── infra/
│   ├── docker-compose.yml
│   └── postgres/init.sql
├── .gitlab-ci.yml
├── build.gradle.kts                # Root build — plugins, JaCoCo, Spotless
└── settings.gradle.kts
```

---

## Kafka Topics

| Topic | Producer | Consumers |
|---|---|---|
| `order-placed` | `order-service` | `payment-service`, `analytics-service` |
| `payment-processed` | `payment-service` | `inventory-service`, `notification-service` |
| `order-failed` | `payment-service` | `notification-service` |
| `inventory-reserved` | `inventory-service` | `notification-service`, `analytics-service` |

**Naming convention:** kebab-case throughout. Consistent, readable, no collision with Kotlin package naming.

---

## Event Flow

1. Client sends `POST /api/v1/orders` to `order-service`
2. `order-service` validates the request, persists the order, and publishes `order-placed`
3. `payment-service` consumes `order-placed`:
   - Success → publishes `payment-processed`
   - Failure → publishes `order-failed` (explicit failure event, no ambiguous status field)
4. `inventory-service` consumes `payment-processed`, reserves stock, publishes `inventory-reserved`
5. `notification-service` operates independent listeners per terminal event:
   - `payment-processed` → "Your payment was successful."
   - `order-failed` → "Your order could not be processed."
   - `inventory-reserved` → "Your order is confirmed."
6. `analytics-service` consumes `order-placed` and `inventory-reserved` for real-time aggregations

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
```

Line items are stored as JSONB on the `orders` table. A separate `order_items` table was considered and rejected — line items are never queried independently within `order-service`; they exist solely as part of the order payload published to Kafka.

### payment-service (`payments_db`)

```sql
TABLE payments
  id            UUID        PRIMARY KEY
  order_id      UUID        NOT NULL UNIQUE
  status        VARCHAR     NOT NULL  -- SUCCESS | FAILED
  processed_at  TIMESTAMP   NOT NULL DEFAULT now()
```

Redis stores idempotency keys to prevent duplicate payment processing on Kafka consumer redelivery.

### inventory-service (`inventory_db`)

```sql
TABLE inventory
  id                  UUID  PRIMARY KEY
  product_id          UUID  NOT NULL UNIQUE
  quantity_available  INT   NOT NULL

TABLE inventory_reservations
  id                UUID        PRIMARY KEY
  order_id          UUID        NOT NULL UNIQUE
  product_id        UUID        NOT NULL
  quantity_reserved INT         NOT NULL
  status            VARCHAR     NOT NULL  -- RESERVED | FAILED
  reserved_at       TIMESTAMP   NOT NULL DEFAULT now()
```

### notification-service
No database. All notifications are mock (logged to stdout).

### analytics-service
No relational database. State maintained in Kafka Streams state stores (RocksDB-backed). Aggregations tracked:
- Orders per minute — windowed count over `order-placed`
- Revenue totals — running sum of `totalPrice` from `order-placed`
- Top products by volume — count per `productId` from `inventory-reserved`

---

## Local Development

### Prerequisites
- Docker Engine 27+ and Docker Compose v2+
- JDK 21
- Gradle (or use the wrapper `./gradlew`)

### Start Everything (Recommended)

Builds and starts all services and infrastructure in Docker:

```bash
docker compose -f infra/docker-compose.yml up --build -d
```

This starts:
- Kafka (KRaft mode) — ports `9092` (container-to-container), `29092` (host access)
- PostgreSQL 17 — port `5432` — creates `orders_db`, `payments_db`, `inventory_db`
- Redis — port `6379`
- `order-service` — port `8080`

To bring everything down:

```bash
docker compose -f infra/docker-compose.yml down
```

To also wipe the PostgreSQL volume (clean slate):

```bash
docker compose -f infra/docker-compose.yml down -v
```

### Run order-service Locally (Against Containerised Infra)

Start only the infrastructure:

```bash
docker compose -f infra/docker-compose.yml up kafka postgres redis -d
```

Then run the service locally:

```bash
./gradlew :order-service:bootRun -Dspring.profiles.active=local
```

The `local` profile connects to `localhost:29092` for Kafka and `localhost:5432` for PostgreSQL.

### Spring Profiles

| Profile | Used when |
|---|---|
| `local` | Running service via `bootRun` against containerised infra |
| `docker` | Running service inside Docker Compose |
| `k8s` | Running in Kubernetes (Minikube/Helm) |
| `test` | Integration tests via Testcontainers |

---

## Code Quality

### Formatting

The project uses [Spotless](https://github.com/diffplug/spotless) with [ktlint](https://pinterest.github.io/ktlint/) for Kotlin formatting.

Auto-format all files:

```bash
./gradlew spotlessApply
```

Check formatting without modifying files (what CI runs):

```bash
./gradlew spotlessCheck
```

### Test Coverage

JaCoCo enforces minimum coverage thresholds on every build:

| Counter | Threshold |
|---|---|
| Instruction | 90% |
| Branch | 80% |

Run tests with coverage verification:

```bash
./gradlew check
```

Coverage reports are generated at `build/reports/jacoco/` per module.

---

## CI/CD Pipeline

GitLab CI pipeline at `.gitlab-ci.yml`. Runs on every push to every branch.

| Stage | Jobs | Runs on |
|---|---|---|
| `validate` | Spotless formatting check | All branches |
| `test` | Unit tests and integration tests (parallel) | All branches |
| `coverage` | JaCoCo verification, report published as artifact | All branches |
| `build` | Build Docker image, push to GitLab registry | `main` only |
| `deploy` | Placeholder — Minikube/Helm (coming soon) | `main` only |

Docker images are tagged with both the commit SHA (`order-service:abc1234`) and `latest`.

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
| Jackson serialization (custom) | Confluent Schema Registry library is in maintenance mode. Custom `EventSerializer`/`EventDeserializer` in shared module is simpler, dependency-free, and fully under our control. Avro/Schema Registry will not be revisited. |
| JSONB for line items | Line items are never queried independently in `order-service`. JSONB avoids a join and scales cleanly for bulk orders. |
| Server-side `totalPrice` calculation | Clients never submit `totalPrice`. The server calculates it from line items. Never trust the client for money. |
| Explicit `order-failed` event | Avoids a `payment-processed` event with a `FAILED` status field, which creates ambiguous consumer contracts. |
| `PENDING` status only (initial) | Order status lifecycle (`PENDING → CONFIRMED → FAILED`) will expand as downstream services are built. |
| Flyway owns the schema | `hibernate.ddl-auto=validate`. Hibernate validates against the schema, never modifies it. |
| Single PostgreSQL container (local) | Three logical databases on one container for local resource efficiency. Production would use isolated instances per service. |
| KRaft mode (no ZooKeeper) | Confluent Platform 8.0 removed ZooKeeper. Single Kafka container with `KAFKA_PROCESS_ROLES: broker,controller`. |
| Dual Kafka listeners | `kafka:9092` for container-to-container communication inside Docker; `localhost:29092` for host machine access during local development. Single listener would require different images per environment. |
| Profile-agnostic Docker image | Spring profile is not baked into the Dockerfile. It is injected via `SPRING_PROFILES_ACTIVE` at the orchestration layer (Docker Compose env var, K8s pod spec). The same image runs in all environments. |
| Multi-stage layered Dockerfile | Build stage compiles and extracts JAR layers; runtime stage uses `eclipse-temurin:21-jre-alpine`. Dependencies layer is cached separately from application code — fast rebuilds on code-only changes. |

---

## Known Simplifications

These are deliberate trade-offs made to ship a working implementation. Each is a candidate for a future iteration.

| Simplification | Production Alternative |
|---|---|
| Kafka publish failure triggers DB rollback via `.get()` | Transactional Outbox Pattern — persist events to a DB outbox table in the same transaction, relay to Kafka asynchronously |
| Payment is a simulation, not a live gateway | Stripe / Adyen sandbox integration |
| Inventory reservation always succeeds | Stock quantity checking with `inventory` table, failure path via `order-failed` |
| No authentication / authorisation | JWT via Spring Security |
| Testcontainers reuse not enabled | Enable Testcontainers reuse to avoid container startup cost on repeated local test runs |

---

## Build Sequence

- [x] Domain model + event schema design
- [x] Project structure + Gradle multi-module setup
- [x] Docker Compose baseline (Kafka, PostgreSQL, Redis)
- [x] `order-service` — REST API, validation, persistence, `order-placed` producer
- [x] `order-service` — unit tests + integration tests (Testcontainers), JaCoCo coverage
- [x] `order-service` — multi-stage Dockerfile, added to Docker Compose
- [x] Code quality — Spotless + ktlint
- [x] GitLab CI pipeline — validate, test, coverage, build, deploy stages
- [ ] `payment-service` — consumer, idempotency, producers
- [ ] `inventory-service` — consumer, reservation, producer
- [ ] `notification-service` — listeners, mock dispatch
- [ ] `analytics-service` — Kafka Streams topology
- [ ] Observability — OpenTelemetry, Grafana dashboards
- [ ] Kubernetes + Helm — Minikube manifests, Helm chart per service