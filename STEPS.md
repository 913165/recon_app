# Running the Recon System — Step-by-Step Guide

Follow these steps in order. Each step assumes the previous one has completed successfully.

---

## Prerequisites

| Tool | Minimum version | Check |
|---|---|---|
| JDK | 25 | `java -version` |
| Maven | 3.9.x (or use the bundled `mvnw`) | `mvn -version` |
| Docker Desktop | 24+ (Engine 26+) | `docker info` |
| Docker Compose | v2 plugin | `docker compose version` |
| Git | any | `git --version` |

---

## Step 1 — Clone / open the project

```powershell
cd C:\googlecloud_work\hdfc_jobserver\recon_app
```

Verify the multi-module layout:

```powershell
Get-ChildItem -Directory | Select-Object Name
```

Expected modules: `recon-common`, `recon-storage`, `recon-ingestion`, `recon-processing`, `recon-notification`, `recon-api`.

---

## Step 2 — Build the project (compile + unit tests)

```powershell
.\mvnw.cmd clean test
```

All unit tests must pass before proceeding. The integration test (`ReconPipelineIntegrationTest`) requires Docker and will be skipped automatically when Docker is not running.

To skip tests entirely for a faster build:

```powershell
.\mvnw.cmd clean package -DskipTests
```

---

## Step 3 — Start infrastructure services (PostgreSQL + Kafka + OTel Collector)

Start only the infrastructure services (not the app container):

```powershell
docker compose up -d postgres kafka otel-collector
```

Wait ~10 seconds for services to become healthy, then verify:

```powershell
docker compose ps
```

All three services should show **running** status.

### Verify PostgreSQL is ready

```powershell
docker compose exec postgres psql -U recon_user -d recondb -c "\l"
```

### Verify Kafka is ready

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

---

## Step 4 — Create the landing zone directory

The file watcher monitors this directory for incoming reconciliation files.

```powershell
New-Item -ItemType Directory -Force -Path ".\data\landing"
```

---

## Step 5 — Run Flyway database migrations (automatic on startup)

Flyway runs automatically when the application starts. The five migration scripts apply in order:

| Script | Creates |
|---|---|
| `V1__create_staging_table.sql` | `recon_staging` table |
| `V2__create_recon_results_table.sql` | `recon_results` table |
| `V3__create_audit_errors_table.sql` | `recon_audit_errors` table |
| `V4__create_file_registry_table.sql` | `recon_file_registry` table |
| `V5__create_tolerance_table.sql` | `rcon_tolerance_config` table |

To run migrations manually before app start:

```powershell
.\mvnw.cmd -pl recon-storage flyway:migrate `
  -Dflyway.url=jdbc:postgresql://localhost:5432/recondb `
  -Dflyway.user=recon_user `
  -Dflyway.password=recon_pass
```

---

## Step 6 — Start the application (local / dev mode)

```powershell
.\mvnw.cmd -pl recon-api spring-boot:run
```

The application will:
1. Connect to PostgreSQL and run any pending Flyway migrations.
2. Connect to Kafka and create required topics (`recon.file.arrived`, `recon.processing.completed`, `recon.break.alerts`).
3. Start the virtual-thread file watcher on `.\data\landing`.
4. Expose the REST API on **http://localhost:8080**.

### Environment variable overrides (optional)

```powershell
$env:DB_USER       = "recon_user"
$env:DB_PASS       = "recon_pass"
$env:KAFKA_SERVERS = "localhost:9092"
$env:LANDING_ZONE_PATH = "C:\googlecloud_work\hdfc_jobserver\recon_app\data\landing"
$env:OTEL_ENDPOINT = "http://localhost:4318/v1/traces"
$env:SLACK_WEBHOOK_URL = "https://hooks.slack.com/..."  # optional
$env:ALERT_EMAIL   = "ops@company.com"                  # optional
.\mvnw.cmd -pl recon-api spring-boot:run
```

---

## Step 7 — Verify the application is running

### Health check

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Expected response:

```json
{ "status": "UP" }
```

### Swagger / OpenAPI UI

Open in a browser:

```
http://localhost:8080/swagger-ui/index.html
```

### Prometheus metrics

```
http://localhost:8080/actuator/prometheus
```

---

## Step 8 — Drop a reconciliation file into the landing zone

The watcher picks up any `.dat`, `.csv`, or `.txt` file placed in `.\data\landing`.

**Pipe-delimited example** (Core Banking):

```powershell
Copy-Item `
  "recon-ingestion\src\test\resources\sample\SRCA_RECON_20260428_093000_001.dat" `
  "data\landing\"
```

Within 5 seconds the watcher will:
1. Register the file in `recon_file_registry`.
2. Publish a `FileArrivedEvent` to the `recon.file.arrived` Kafka topic.
3. Trigger the parsing and validation pipeline.
4. Write matched/break results to `recon_results`.

---

## Step 9 — Query reconciliation results via REST API

```powershell
# List breaks for today
Invoke-RestMethod `
  "http://localhost:8080/api/v1/recon/results?reportDate=2026-04-28&status=BREAK&page=0&size=50"

# Daily summary
Invoke-RestMethod `
  "http://localhost:8080/api/v1/recon/summary?reportDate=2026-04-28"

# Mark a break as resolved
Invoke-RestMethod -Method Patch `
  -Uri "http://localhost:8080/api/v1/recon/results/{reconId}/resolve" `
  -ContentType "application/json" `
  -Body '{"resolutionNote":"Timing difference confirmed"}'

# File registry status
Invoke-RestMethod `
  "http://localhost:8080/api/v1/files?reportDate=2026-04-28&sourceSystem=CORE_BANKING"
```

---

## Step 10 — Run the full application via Docker Compose (production-like)

Build the application JAR first:

```powershell
.\mvnw.cmd clean package -DskipTests
```

Then start everything including the app container:

```powershell
docker compose up --build -d
```

View logs:

```powershell
docker compose logs -f recon-app
```

Stop everything:

```powershell
docker compose down
```

Stop and remove volumes (wipes the database):

```powershell
docker compose down -v
```

---

## Step 11 — Run the integration test suite (requires Docker)

Start Docker Desktop, then:

```powershell
.\mvnw.cmd test -pl recon-api
```

The `ReconPipelineIntegrationTest` will spin up Testcontainers (PostgreSQL 17 + Kafka 4.2.0) automatically and tear them down after the test run.

---

## Useful commands reference

| Action | Command |
|---|---|
| Build all modules | `.\mvnw.cmd clean package -DskipTests` |
| Run all unit tests | `.\mvnw.cmd test` |
| Run single module tests | `.\mvnw.cmd test -pl recon-processing` |
| Check Kafka topics | `docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list` |
| Tail Kafka topic | `docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic recon.file.arrived --from-beginning` |
| Connect to PostgreSQL | `docker compose exec postgres psql -U recon_user -d recondb` |
| View all recon breaks | `SELECT * FROM recon_results WHERE match_status = 'BREAK' ORDER BY created_at DESC LIMIT 20;` |
| View file registry | `SELECT file_id, file_name, file_status, total_records FROM recon_file_registry ORDER BY received_at DESC;` |
| Actuator health | `Invoke-RestMethod http://localhost:8080/actuator/health` |
| Prometheus metrics | `Invoke-RestMethod http://localhost:8080/actuator/prometheus` |

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `Connection refused` on port 5432 | PostgreSQL container not started | `docker compose up -d postgres` |
| `Connection refused` on port 9092 | Kafka container not started | `docker compose up -d kafka` |
| `FlywayException: validate failed` | Schema out of sync | Run `.\mvnw.cmd -pl recon-storage flyway:repair` then restart |
| `ControlTotalMismatchException` on ingest | HDR/TRL totals don't match DTL sum | Correct the source file and re-drop |
| Application starts but no file processing | Landing zone path wrong | Check `LANDING_ZONE_PATH` env var matches actual directory |
| Swagger UI returns 403 | Spring Security blocking | Add `http://localhost:8080/swagger-ui/**` to the security permit list in `SecurityConfig` |
| OTel traces not appearing | Collector not reachable | Check `OTEL_ENDPOINT` points to the collector's OTLP HTTP endpoint |

