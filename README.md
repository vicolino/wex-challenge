# WEX Corporate Payments — Purchase Transactions

Service that stores USD purchase transactions and retrieves them converted to a
target currency using the U.S. Department of the Treasury's
[Reporting Rates of Exchange](https://fiscaldata.treasury.gov/datasets/treasury-reporting-rates-exchange/treasury-reporting-rates-of-exchange)
dataset.

Built for the WEX SDE 4 (R21202) take-home challenge.

---

## Stack

| Concern         | Choice                                                              |
| --------------- | ------------------------------------------------------------------- |
| Language        | Java 21 (LTS)                                                       |
| Framework       | Spring Boot 4.0 / Spring Framework 7 (Web, Validation, Data JPA, Actuator) |
| JSON            | Jackson 3 (`tools.jackson`)                                        |
| Persistence     | PostgreSQL (dev/hom/prod); file-backed H2 for `local`              |
| Migrations      | Flyway (`validate` everywhere — Hibernate never issues DDL)        |
| HTTP client     | Spring HTTP Interface (`@HttpExchange`) over `RestClient`           |
| API docs        | `springdoc-openapi` (Swagger UI at `/swagger-ui.html`)              |
| Observability   | Micrometer + OpenTelemetry over OTLP (traces + metrics), wired off-by-default |
| Tests           | JUnit 5, Mockito, AssertJ; sliced integration (`@WebMvcTest` + `RestTestClient` + `@MockitoBean`, `MockRestServiceServer`, `@DataJpaTest`) |
| Build / package | Gradle 9.5 (Groovy DSL), executable Spring Boot jar, Dockerfile     |

---

## Quick start

### Prerequisites

You need **one** of the following:

- **Java 21** (the Gradle Wrapper is committed — `./gradlew` works out of the box), **or**
- **Docker** (Engine 20+).

### Run locally (Gradle)

```bash
./gradlew bootRun
```

The service comes up on `http://localhost:8080` using the default `local`
profile (file-backed H2 — no database to install). To run against PostgreSQL,
set a profile and the connection env vars:

```bash
SPRING_PROFILES_ACTIVE=dev \
POSTGRES_URL=jdbc:postgresql://localhost:5432/wex_dev \
POSTGRES_USER=wex POSTGRES_PASSWORD=wex \
./gradlew bootRun
```

### Run as an executable jar

```bash
./gradlew bootJar
java -jar build/libs/wex-challenge.jar
```

### Run with Docker

```bash
docker build -t wex-challenge .
docker run --rm -p 8080:8080 wex-challenge
```

### Run the tests

Tests are split into Gradle JVM test suites, each with its own source set under
`src/`. Shared test-data builders live in a `testFixtures` source set so both
the unit suite and the integration slices reuse them:

| Suite              | Source set               | What it covers                                                          |
| ------------------ | ------------------------ | ---------------------------------------------------------------------- |
| `test`             | `src/test/`              | Pure unit tests only — no Spring context, no I/O (domain, services with mocked collaborators, config wiring, the exception handler). |
| `architectureTest` | `src/test-architecture/` | ArchUnit invariants over the production code.                          |
| `integrationTest`  | `src/test-integration/`  | Integration slices — one boundary each, no `@SpringBootTest` (web, client, database). |
| _(fixtures)_       | `src/test-fixtures/`     | Shared test-data builders consumed by both `test` and `integrationTest`. |

```bash
./gradlew test                # unit suite
./gradlew architectureTest    # ArchUnit suite
./gradlew integrationTest     # integration slices (web, client, db)
./gradlew allTests            # all three, in order: test → architecture → integration
./gradlew check               # allTests + coverage verification + reports
```

The suites run sequentially (`mustRunAfter`) so a broken architectural
invariant or a failing integration is surfaced *after* unit tests pass.

**Integration by slices, not end-to-end.** There is deliberately no
`@SpringBootTest` that boots the whole application. Each integration test
(suffixed `IT`) exercises exactly one layer in isolation, which keeps them fast
and pinpoints failures to a single boundary:

| Layer    | Slice (`src/test-integration/`)                    | What it proves                                                              |
| -------- | -------------------------------------------------- | -------------------------------------------------------------------------- |
| Web      | `@WebMvcTest` + `RestTestClient` (service `@MockitoBean`) | Routing, validation, serialization, status codes, the RFC 7807 error model. |
| Client   | `RestClient` + `MockRestServiceServer` (no Spring ctx) | URL/JSON handling and timeout/error → exception mapping for the HTTP Interface client. |
| Database | `@DataJpaTest` (`replace = NONE`) + H2 + Flyway    | The JPA mapping matches the real migrated schema (`ddl-auto: validate`) and round-trips. |

The web slices share a small `WebTestBase` that holds the auto-configured
`MockMvc`; each test drives it through Spring 7's fluent `RestTestClient`.

### Code coverage (JaCoCo)

Coverage data from all three suites is aggregated into a single report.

```bash
./gradlew jacocoTestReport                # generate HTML + XML report
./gradlew jacocoTestCoverageVerification  # fail if thresholds aren't met
open build/reports/jacoco/test/html/index.html
```

Verification thresholds (`gradle check` enforces these):

| Counter      | Minimum |
| ------------ | ------- |
| Line         | 100 %   |
| Branch       | 100 %   |
| Instruction  | 100 %   |

Three classes are excluded from coverage — all pure declarative wiring with no
branches or logic worth asserting in isolation, and (now that the suite is
sliced rather than booting the whole app) nothing instantiates them:
`WexChallengeApplication` (its `main()` delegates to `SpringApplication.run`),
`OpenApiConfig` (builds static springdoc metadata) and `CacheConfig` (an empty
`@EnableCaching` marker). `RestClientConfig` — which has real SSL/truststore
logic — is covered by a focused unit test, and every other class, including DTO
factories, exception types and the defensive null-guard branches that ordinary
HTTP traffic doesn't reach, is exercised by tests.

Current numbers (`./gradlew jacocoTestReport`):

| Counter      | Covered  |
| ------------ | -------- |
| Line         | 196/196 (100 %) |
| Branch       | 28/28 (100 %)   |
| Instruction  | 845/845 (100 %) |
| Method       | 60/60 (100 %)   |
| Class        | 18/18 (100 %)   |

---

## API

Interactive docs: <http://localhost:8080/swagger-ui.html>
OpenAPI JSON:    <http://localhost:8080/v3/api-docs>

### `POST /api/v1/purchase-transactions`

Store a new USD purchase transaction (Requirement #1).

Request:

```json
{
  "description": "Office supplies",
  "transactionDate": "2025-12-01",
  "purchaseAmount": 99.99
}
```

Field rules:

| Field             | Rule                                                                |
| ----------------- | ------------------------------------------------------------------- |
| `description`     | Required. Max 50 characters.                                        |
| `transactionDate` | Required. ISO-8601 date (`yyyy-MM-dd`).                             |
| `purchaseAmount`  | Required. Positive. Stored rounded to the nearest cent (HALF_UP).   |

Response `201 Created`:

Empty body. The `Location` header points to the created resource, including the generated id:

```
Location: /api/v1/purchase-transactions/11111111-1111-1111-1111-111111111111
```

Fetch the resource with `GET /api/v1/purchase-transactions/{id}`.

### `GET /api/v1/purchase-transactions/{id}`

Returns the stored transaction in its original USD amount.

### `GET /api/v1/purchase-transactions/{id}/converted?currency=Canada-Dollar`

Returns the purchase converted to the target currency (Requirement #2).

- `currency` is the Treasury dataset's `country_currency_desc` value, e.g.
  `Canada-Dollar`, `Mexico-Peso`, `Euro Zone-Euro`, `Brazil-Real`.
- The service uses the most recent rate whose `record_date` is on or before the
  purchase date and within the last **6 months**.
- If no such rate exists, the call returns `422 Unprocessable Entity` with an
  RFC 7807 problem detail.
- The converted amount is rounded to two decimal places (HALF_UP).

Response `200 OK`:

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "description": "Office supplies",
  "transactionDate": "2025-12-01",
  "originalAmountUsd": 99.99,
  "targetCurrency": "Canada-Dollar",
  "exchangeRate": 1.358,
  "exchangeRateDate": "2025-09-30",
  "convertedAmount": 135.79
}
```

### Errors

Every error returns an RFC 7807 `application/problem+json` body:

| Status | When                                                                          |
| ------ | ----------------------------------------------------------------------------- |
| 400    | Validation error (bad body, missing query param, bad date, negative amount). |
| 404    | Transaction id not found.                                                    |
| 422    | No exchange rate available within the 6-month window.                        |
| 502    | Upstream Treasury API failure (network error, 5xx, malformed body).          |

Example body:

```json
{
  "type": "about:blank",
  "title": "Exchange rate unavailable",
  "status": 422,
  "detail": "The purchase cannot be converted to the target currency: ...",
  "targetCurrency": "Atlantis-Gold",
  "purchaseDate": "2025-12-01"
}
```

---

## curl examples

```bash
# Create
curl -sS -X POST http://localhost:8080/api/v1/purchase-transactions \
  -H 'Content-Type: application/json' \
  -d '{"description":"Office supplies","transactionDate":"2025-12-01","purchaseAmount":99.99}'

# Retrieve original
curl -sS http://localhost:8080/api/v1/purchase-transactions/<id>

# Retrieve converted
curl -sS "http://localhost:8080/api/v1/purchase-transactions/<id>/converted?currency=Canada-Dollar"
```

---

## Production-grade concerns

### Caching (Caffeine)

Treasury rates are immutable once published, so `findLatestRate(currency,
purchaseDate, windowStart)` is wrapped with `@Cacheable("treasuryRates", sync = true)`.
A cache hit drops conversion latency from ~300 ms to <1 ms.

- 24 h TTL, 10 000 entry max, stats recorded.
- `sync = true` collapses concurrent first-time lookups into one upstream call.
- Inspectable at `GET /actuator/caches`.

### Resilience (Resilience4j)

The Treasury client is wrapped with `@Retry` and `@CircuitBreaker` named
`treasury`. 4xx is **never** retried (caller's fault); 5xx and network errors
retry up to 3 times with exponential backoff. After 50% failure across a
20-call window the breaker opens for 30 s, then admits 3 half-open trial calls.

- Live state at `GET /actuator/circuitbreakers` and `GET /actuator/retries`.
- Health indicator surfaces in `/actuator/health`.

The cache advisor is given `Ordered.HIGHEST_PRECEDENCE` so cache hits
short-circuit the resilience chain entirely (no retry budget or breaker stats
consumed on a hit).

### Observability (OpenTelemetry — wired, vendor-neutral)

The three pillars are instrumented with **Micrometer + OpenTelemetry**, exported
over **OTLP**, and **off by default** so the service boots with no collector
present. There is no visualization backend bundled — that is intentionally the
one pluggable piece (see below).

| Signal      | How it's produced                                                            | Exported via                         |
| ----------- | ---------------------------------------------------------------------------- | ------------------------------------ |
| **Metrics** | Micrometer (JVM, HTTP server, cache, Resilience4j, HikariCP) via Actuator    | `micrometer-registry-otlp` → OTLP    |
| **Traces**  | Micrometer Tracing with the OpenTelemetry bridge (auto context propagation)  | `opentelemetry-exporter-otlp` → OTLP |
| **Logs**    | Logback to stdout; correlated with `traceId`/`spanId` once tracing is on     | container stdout → log shipper       |

Everything is driven by env vars — point them at a collector to light it up:

```bash
OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318 \
TRACING_ENABLED=true \
OTEL_METRICS_ENABLED=true \
java -jar build/libs/wex-challenge.jar
```

| Variable                      | Default                  | Effect                                  |
| ----------------------------- | ------------------------ | --------------------------------------- |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4318`  | Base OTLP endpoint for traces + metrics |
| `TRACING_ENABLED`             | `false`                  | Turns span export on                    |
| `OTEL_METRICS_ENABLED`        | `false`                  | Turns OTLP metric push on               |
| `TRACING_SAMPLE_PROBABILITY`  | `1.0`                    | Trace sampling rate                     |
| `OTEL_METRICS_STEP`           | `30s`                    | Metric push interval                    |

**Why "prepared" and not "running":** because the *visualization* layer is a
deployment choice, not an application one. The app speaks OTLP — the open
standard — so it drops straight into either of the usual stacks without code
changes:

- **Grafana stack** — OTel Collector → **Tempo** (traces) + **Prometheus**/Mimir
  (metrics) + **Loki** (logs), all viewed in Grafana.
- **Elastic / ELK** — OTel Collector → Elasticsearch, explored in Kibana (APM).

Adding it is purely ops: run an OpenTelemetry Collector (or Grafana Alloy) as a
sidecar, set `OTEL_EXPORTER_OTLP_ENDPOINT`, and the signals already emitted here
start flowing. Until then, metrics remain available for scraping/inspection at
`GET /actuator/metrics`.

### Architecture tests (ArchUnit)

`LayeredArchitectureTest` enforces, at compile-time-of-tests, that:

- The domain stays free of Spring, web, repository, or service dependencies.
- Web cannot reach repositories directly.
- Repositories cannot be accessed outside the service layer.
- `@RestController`, `@Service`, JPA `@Entity` and Spring Data interfaces stay
  in their canonical packages.
- No `@Autowired` field injection — constructor injection only.

If a future PR violates one of these invariants, `./gradlew test` fails.

---

## Design notes

### Financial precision

- Amounts are `BigDecimal` everywhere — never `double` or `float`.
- The entity normalises the stored amount to scale 2, `RoundingMode.HALF_UP`,
  matching the brief's "rounded to the nearest cent" rule. Conversion uses the
  same rounding mode on the final two-decimal result.
- Validation guarantees positive amounts; the entity defends a second time so
  the database can never hold a non-positive amount regardless of caller.

### Six-month look-back

- The window start is computed as `purchaseDate.minusMonths(6)`.
- The query uses both `record_date:lte:<purchaseDate>` and
  `record_date:gte:<windowStart>` filters plus `sort=-record_date` with
  `page[size]=1`, so the Treasury API returns at most one record: the most
  recent rate inside the window.
- The service double-checks the returned date is in range — if a misbehaving
  upstream slips a rate outside the window, the call still fails closed.

### Profiles and persistence

Configuration is split by Spring profile:

| Profile           | Database                                  | Notes                                                          |
| ----------------- | ----------------------------------------- | -------------------------------------------------------------- |
| `local` (default) | File-backed H2 (`./data/wex`), PG mode    | Zero setup — `./gradlew bootRun` just works. Data survives restarts; the file is gitignored. |
| `dev`             | PostgreSQL                                | Connection from `POSTGRES_URL/USER/PASSWORD` (local defaults). |
| `hom`             | PostgreSQL                                | All connection details required from the environment.          |
| `prod`            | PostgreSQL                                | Env-only, Hikari pool tuning, health details hidden.           |
| `test`            | In-memory H2, PG mode                     | Used by the integration suite; clean DB per run.               |

Select a profile with `SPRING_PROFILES_ACTIVE=dev` (or `-Dspring.profiles.active=dev`).
The default is `local` so a fresh clone runs with no external dependencies — the
"plug and play" experience the brief asks for.

The local and test profiles run H2 in **PostgreSQL-compatibility mode**, so the
exact same Flyway migration that builds real PostgreSQL also builds the H2
schema. There's a single source of truth for the schema.

### Why Flyway with `ddl-auto: validate`

The schema is owned by Flyway migrations (`src/main/resources/db/migration`).
Hibernate is set to `validate` in every profile, so it only checks that the JPA
mapping matches the migrated schema — it never issues DDL itself. This is the
production-safe posture: schema changes are explicit, versioned, and reviewed,
never inferred from entity changes at boot. Because migrations run in `local`
and `test` too, the migration is exercised on every test run.

### Why a declarative HTTP Interface (`@HttpExchange`)

The Treasury call is modelled as a declarative `TreasuryRatesApi` interface
annotated with `@GetExchange`. At startup a proxy is created with
`HttpServiceProxyFactory` over a `RestClient` (Spring 6.1+'s synchronous client,
the successor to `RestTemplate`). That `RestClient` carries the tuned timeouts
and the bundled Treasury trust anchor, so the declarative layer stays free of
transport concerns. `TreasuryExchangeRateClient` keeps the business mapping
(filter construction, 6-month guard, error translation) plus the cache and
resilience annotations, and simply delegates the HTTP call to the interface.

### Why MockRestServiceServer for the client slice

The client slice needs a deterministic Treasury API. `TreasuryExchangeRateClientIT`
(via `HttpExchangeClientTestBase`) binds Spring's `MockRestServiceServer` to a
real `RestClient` and creates the `@HttpExchange` proxy over it. This exercises
the actual HTTP path — URL building, JSON parsing and the timeout/5xx → exception
translation — without a Spring context, without WireMock, and without ever
calling the public API. Using the framework's own client-side mock keeps the
slice dependency-light and is the same pattern reused for any future HTTP client.

### Why a shared `testFixtures` source set

The test-data builders (`PurchaseTransactionFixture`, `ExchangeRateFixture`,
`ConvertedPurchaseFixture`) are needed by both the unit suite (`src/test/`) and
the integration slices (`src/test-integration/`). Rather than duplicating them
or letting one suite reach into the other's source set, they live in their own
`src/test-fixtures/` source set (Gradle's `java-test-fixtures` plugin). Both
suites declare `testFixtures(project())`:

```groovy
plugins { id 'java-test-fixtures' }

testing.suites {
    test            { dependencies { implementation testFixtures(project()) } }
    integrationTest { dependencies { implementation testFixtures(project()) } }
}
```

The fixtures depend only on production types, so they compile against `main`
with no test-framework leakage, and they are excluded from coverage (they are
test scaffolding, not production code).

### Error model

All error responses follow RFC 7807 (`ProblemDetail`). Validation errors include
a structured `errors` array of `{field, message}` so clients can render them
without re-parsing prose.

---

## Project layout

```
build.gradle
settings.gradle
gradle/wrapper/{gradle-wrapper.jar, gradle-wrapper.properties}
gradlew, gradlew.bat
Dockerfile
src/main/java/com/wex/challenge
├── WexChallengeApplication.java
├── config/
│   ├── CacheConfig.java            @EnableCaching + advisor order
│   ├── OpenApiConfig.java          OpenAPI / Swagger metadata
│   ├── RestClientConfig.java       RestClient + @HttpExchange proxy beans
│   └── TreasuryProperties.java     @ConfigurationProperties
├── domain/
│   └── PurchaseTransaction.java    JPA entity + invariants
├── repository/
│   └── PurchaseTransactionRepository.java
├── service/
│   ├── ExchangeRate.java
│   ├── TreasuryRatesApi.java             @HttpExchange declarative client
│   ├── TreasuryExchangeRateClient.java   @Cacheable @Retry @CircuitBreaker
│   ├── PurchaseTransactionService.java
│   └── CurrencyConversionService.java
├── exception/
│   ├── PurchaseTransactionNotFoundException.java
│   ├── ExchangeRateNotAvailableException.java
│   └── TreasuryApiException.java
└── web/
    ├── PurchaseTransactionController.java
    ├── GlobalExceptionHandler.java
    └── dto/...

src/main/resources/
├── application.yml                  Common config (default profile: local)
├── application-local.yml            File-backed H2 (default)
├── application-dev.yml              PostgreSQL (dev)
├── application-hom.yml              PostgreSQL (homologation)
├── application-prod.yml             PostgreSQL (production)
├── certs/sectigo-r46.pem            Treasury TLS trust anchor
└── db/migration/
    └── V1__create_purchase_transactions.sql

src/test/java/                       Unit tests only (no Spring context, no I/O)
src/test-fixtures/java/              Shared test-data builders (java-test-fixtures)
src/test-architecture/java/          ArchUnit suite (LayeredArchitectureTest)
src/test-integration/java/           Integration slices (…IT): @WebMvcTest, client, @DataJpaTest
    ├── web/         WebTestBase + PurchaseTransactionControllerIT (RestTestClient)
    ├── service/     HttpExchangeClientTestBase + TreasuryExchangeRateClientIT (MockRestServiceServer)
    └── persistence/ PurchaseTransactionRepositoryIT (@DataJpaTest, H2 + Flyway)
```

---

## Test coverage

| Suite              | Test                                       | What it proves                                                   |
| ------------------ | ------------------------------------------ | ---------------------------------------------------------------- |
| `test`             | `PurchaseTransactionTest`                  | Validation rules and HALF_UP rounding at boundaries.             |
| `test`             | `PurchaseTransactionServiceTest`           | Persistence orchestration and not-found handling (mocked repo).  |
| `test`             | `CurrencyConversionServiceTest`            | Math, 6-month window computation, error paths.                   |
| `test`             | `RestClientConfigTest`                     | Treasury `RestClient`/proxy wiring incl. SSL truststore setup.   |
| `test`             | `GlobalExceptionHandlerTest`               | RFC 7807 problem-detail mapping fallback.                        |
| `architectureTest` | `LayeredArchitectureTest`                  | Layering invariants enforced via ArchUnit.                       |
| `integrationTest`  | `PurchaseTransactionControllerIT`          | Web slice: routing, validation, status codes, error model via `@WebMvcTest` + `RestTestClient`. |
| `integrationTest`  | `TreasuryExchangeRateClientIT`             | Client slice (`MockRestServiceServer`): rate parsing, empty/null data, 5xx, malformed body. |
| `integrationTest`  | `PurchaseTransactionRepositoryIT`          | DB slice: JPA mapping vs. migrated schema + round-trip on H2.    |
