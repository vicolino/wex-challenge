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
| Tests           | JUnit 5, Mockito, AssertJ, MockMvc, WireMock                        |
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

Tests are split into three Gradle JVM test suites, each with its own source
set under `src/`:

| Suite              | Source set               | What it covers                                                |
| ------------------ | ------------------------ | ------------------------------------------------------------- |
| `test`             | `src/test/`              | Fast unit tests (domain, services, controller with MockMvc).  |
| `architectureTest` | `src/test-architecture/` | ArchUnit invariants over the production code.                 |
| `integrationTest`  | `src/test-integration/`  | `@SpringBootTest` end-to-end against H2 and a WireMock'd Treasury. |

```bash
./gradlew test                # unit suite only
./gradlew architectureTest    # ArchUnit suite
./gradlew integrationTest     # end-to-end suite
./gradlew allTests            # all three, in order: test → architecture → integration
./gradlew check               # allTests + coverage verification + reports
```

The suites run sequentially (`mustRunAfter`) so a broken architectural
invariant or a failing integration is surfaced *after* unit tests pass.

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

The only class excluded from coverage is the `WexChallengeApplication`
bootstrapper (its `main()` just delegates to `SpringApplication.run`). Every
other class is exercised by tests, including configuration beans, DTO
factories, exception types, and the defensive null-guard branches that
ordinary HTTP traffic doesn't reach.

Current numbers (`./gradlew jacocoTestReport`):

| Counter      | Covered  |
| ------------ | -------- |
| Line         | 192/192 (100 %) |
| Branch       | 28/28 (100 %)   |
| Instruction  | 852/852 (100 %) |
| Method       | 61/61 (100 %)   |
| Class        | 20/20 (100 %)   |

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

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "description": "Office supplies",
  "transactionDate": "2025-12-01",
  "purchaseAmount": 99.99
}
```

The `Location` header points to the created resource.

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

### Why WireMock

The integration tests need a deterministic Treasury API. WireMock runs in-process
on a random port; `@DynamicPropertySource` rewires
`treasury.base-url` to point at it so the production client code is exercised
end to end without ever calling the public API.

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

src/test/java/                       Unit suite
src/test-architecture/java/          ArchUnit suite (LayeredArchitectureTest)
src/test-integration/java/           @SpringBootTest suite (test profile, in-mem H2)
```

---

## Test coverage

| Suite              | Test                                       | What it proves                                                   |
| ------------------ | ------------------------------------------ | ---------------------------------------------------------------- |
| `test`             | `PurchaseTransactionTest`                  | Validation rules and HALF_UP rounding at boundaries.             |
| `test`             | `CurrencyConversionServiceTest`            | Math, 6-month window computation, error paths.                   |
| `test`             | `TreasuryExchangeRateClientTest` (WireMock)| URL, query parameters, empty data, 5xx, malformed body.          |
| `test`             | `PurchaseTransactionControllerTest`        | Request/response shape and validation behaviour via MockMvc.     |
| `architectureTest` | `LayeredArchitectureTest`                  | Layering invariants enforced via ArchUnit.                       |
| `integrationTest`  | `PurchaseTransactionIntegrationTest`       | Full `@SpringBootTest` stack against a fake Treasury (WireMock). |
