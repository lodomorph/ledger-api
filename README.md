# Ledger API

Account ledger guinea pig for TrueCD pipeline validation. Built with Spring Boot 3.3, PostgreSQL 15, and Redis 7.

## Prerequisites

- Java 17+ (compiles to 17, runs on 17–23)
- Maven 3.9+
- Docker (for Testcontainers integration tests and running infrastructure)
- PostgreSQL 15
- Redis 7

## Quick start (Docker Compose)

Spin up PostgreSQL and Redis, then run the app:

```bash
# Start infrastructure
docker run -d --name ledger-postgres \
  -e POSTGRES_DB=ledger \
  -e POSTGRES_USER=ledger \
  -e POSTGRES_PASSWORD=ledger \
  -p 5432:5432 \
  postgres:15-alpine

docker run -d --name ledger-redis \
  -p 6379:6379 \
  redis:7-alpine

# Build and run
mvn clean package -DskipTests
mvn spring-boot:run
```

The app starts on **http://localhost:8080**.

## Environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `ledger` | Database name |
| `DB_USER` | `ledger` | Database username |
| `DB_PASSWORD` | `ledger` | Database password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |

Override them as needed:

```bash
DB_HOST=mydb.example.com DB_PASSWORD=secret mvn spring-boot:run
```

## Build commands

```bash
# Compile only
mvn compile

# Run unit tests (no Docker needed)
mvn test -Dtest="AccountServiceTest,TransactionServiceTest,AccountNumberGeneratorTest"

# Run integration tests (requires Docker for Testcontainers)
mvn test -Dtest="AccountControllerIT"

# Run all tests
mvn test

# Full build with coverage check (JaCoCo enforces ≥60% line coverage)
mvn verify

# Package the jar (skip tests)
mvn clean package -DskipTests
```

## Running with Docker

```bash
# Build the image
docker build -t ledger-api .

# Run (assumes PostgreSQL and Redis are accessible)
docker run -p 8080:8080 \
  -e DB_HOST=host.docker.internal \
  -e REDIS_HOST=host.docker.internal \
  ledger-api
```

## API endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/accounts` | Create a new account |
| `GET` | `/accounts/{id}` | Get account by UUID |
| `GET` | `/accounts/number/{accountNumber}` | Get account by account number |
| `PATCH` | `/accounts/{id}/freeze` | Freeze an account |
| `PATCH` | `/accounts/{id}/close` | Close an account |
| `POST` | `/accounts/{id}/transactions` | Post a credit or debit |
| `GET` | `/accounts/{id}/transactions` | List transactions (newest first) |
| `GET` | `/accounts/{id}/balance` | Get balance (Redis-cached, 60s TTL) |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/actuator/health/readiness` | Readiness probe |

## Swagger UI & OpenAPI

- Swagger UI: http://localhost:8080/swagger-ui.html
- Live OpenAPI JSON: http://localhost:8080/api-docs
- Static spec: [`openapi.yaml`](openapi.yaml) (used by pipeline gates 1, 3, 5)

## Testing

### Unit tests (Gate 2)

Pure Mockito — no Spring context or Docker required:

- `AccountServiceTest` — create, get, freeze, close
- `TransactionServiceTest` — credit, debit, insufficient funds, frozen/closed account
- `AccountNumberGeneratorTest` — format validation, uniqueness retry, max attempts

### Integration tests (Gate 4)

Uses `@Testcontainers` with real PostgreSQL and Redis containers:

- `AccountControllerIT` — full HTTP round-trip through the entire stack

### Coverage

JaCoCo enforces ≥60% line coverage on `mvn verify`. Report is written to `target/site/jacoco/jacoco.xml`.

## Database migrations

Flyway manages the schema. Migrations live in `src/main/resources/db/migration/`:

- `V1__create_accounts.sql` — accounts table
- `V2__create_transactions.sql` — transactions table with FK and indexes

Hibernate is set to `validate` — it checks the schema but never modifies it.

## Project layout

```
src/main/java/com/gsi77/ledger/
├── LedgerApiApplication.java
├── account/          Entity, repo, service, controller, mapper, DTOs
├── transaction/      Entity, repo, service, mapper, DTOs
├── balance/          Redis-cached balance service + DTO
├── exception/        Custom exceptions + GlobalExceptionHandler
└── config/           Redis and OpenAPI configuration
```
