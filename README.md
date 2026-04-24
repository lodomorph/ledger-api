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

- Swagger UI: http://localhost:8080/swagger-ui/index.html
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

## Deploying to EC2

Everything is handled by a single command — `deploy-app.sh` bootstraps a fresh server
on first run, then builds and ships the JAR. Subsequent runs skip the bootstrap and
just redeploy.

### What gets installed on the server

One Ubuntu server runs all four components natively (no Docker):

| Component | Version | systemd service |
|-----------|---------|-----------------|
| Java app (Spring Boot) | 17 JRE | `ledger-api` |
| PostgreSQL | 15 | `postgresql` |
| Redis | 7 | `redis-server` |
| Caddy (reverse proxy / TLS) | 2.9 | `caddy` |

All services are enabled so they survive reboots. Boot order is enforced by
systemd: PostgreSQL and Redis must be ready before `ledger-api` starts.

### Recommended instance

`t4g.small` — ARM64, 2 vCPU, 2 GB RAM (~$12/month on AWS).  
`t4g.micro` (1 GB) also works; the script adds a 512 MB swap file as a safety net.

### EC2 prerequisites

Before running the script, ensure:

1. **AMI**: Ubuntu 22.04 or 24.04 LTS
2. **Instance type**: `t4g.small` (ARM) or `t3.small` (x86)
3. **Key pair**: your `.pem` key is attached
4. **Security group** — inbound rules:

| Port | Source | Purpose |
|------|--------|---------|
| 22 | Your IP | SSH |
| 80 | 0.0.0.0/0 | HTTP |
| 443 | 0.0.0.0/0 | HTTPS |

For automatic HTTPS, point your domain's DNS A record at the EC2 public IP before
running the script.

### Running the deploy

```bash
# Minimal — plain HTTP on port 80
SSH_KEY=~/Downloads/key.pem ./deploy/deploy-app.sh ubuntu@<EC2-IP>

# With a domain (Caddy will obtain a Let's Encrypt certificate automatically)
SSH_KEY=~/Downloads/key.pem \
CADDY_DOMAIN=api.example.com \
DB_PASSWORD=secret \
./deploy/deploy-app.sh ubuntu@<EC2-IP>
```

**Environment variables:**

| Variable | Default | Description |
|----------|---------|-------------|
| `SSH_KEY` | _(none)_ | Path to `.pem` file |
| `DB_PASSWORD` | `ledger` | PostgreSQL password set on the server |
| `CADDY_DOMAIN` | `:80` | Domain for Caddy; set for automatic HTTPS |

### What the script does

On **first run** against a fresh server (bootstrap + deploy, ~5–7 min total):

1. Detects the server is not yet bootstrapped
2. Copies `user-data.sh` to the server and runs it as root
3. Installs Java, PostgreSQL, Redis, Caddy
4. Creates the `ledger` DB user and database
5. Writes `/etc/ledger-api.env` (secrets) and all systemd unit files
6. Enables all services for reboot survival
7. Builds the fat JAR locally — `mvn clean package -DskipTests`
8. Uploads the JAR and installs it at `/opt/ledger-api/app.jar`
9. Starts `ledger-api` and polls `/actuator/health` until `UP`

On **subsequent runs** (redeploy only, ~1–2 min):

1. Detects bootstrap is already done — skips it
2. Builds the fat JAR
3. Uploads and installs the JAR
4. Restarts the service and polls health

Expected output (first run):

```
==> Checking if server needs bootstrapping...
==> Server not bootstrapped — running setup (this takes ~5 minutes)...
==> Bootstrap complete.
==> Building fat JAR (skipping tests)...
==> Built: target/ledger-api-1.0.0-SNAPSHOT.jar
==> Uploading to ubuntu@<IP>:/tmp/app.jar ...
==> Installing JAR and restarting ledger-api...
==> Waiting for health check (up to 60s)...
    [1/12] no response yet
    [3/12] "status":"UP"
==> Deployment successful. App is UP.
```

### Verifying

```bash
curl https://api.example.com/actuator/health
curl https://api.example.com/api-docs
open https://api.example.com/swagger-ui/index.html
```

### Ops reference

All commands run on the server over SSH.

```bash
# Service status
sudo systemctl status ledger-api caddy postgresql redis-server

# Application logs
sudo journalctl -u ledger-api -f

# Caddy access logs
sudo tail -f /var/log/caddy/access.log

# Change domain or enable HTTPS after initial deploy
sudo nano /etc/caddy/Caddyfile
sudo systemctl reload caddy

# Change DB password or Redis config
sudo nano /etc/ledger-api.env
sudo systemctl restart ledger-api
```

### Deploy directory layout

```
deploy/
├── deploy-app.sh       Single entry point — bootstrap + build + ship
├── user-data.sh        Server bootstrap (called by deploy-app.sh automatically)
├── Caddyfile           Reference Caddy config (live file: /etc/caddy/Caddyfile)
└── ledger-api.service  Reference systemd unit (live file: /etc/systemd/system/)
```

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
