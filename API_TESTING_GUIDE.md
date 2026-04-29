# RCON Indian Payment Reconciliation System — REST API Testing & Learning Guide

> **Purpose:** A hands-on, step-by-step walkthrough of the entire application from first startup through
> file ingestion, matching, break resolution, and monitoring — for Indian payment channels (UPI, IMPS, NEFT, RTGS).
> **Audience:** Developers and testers who want to understand how the system works end-to-end.
> **Date:** 2026-04-29

---

## Table of Contents

1. [Understanding the Architecture](#1-understanding-the-architecture)
2. [Prerequisite Checks](#2-prerequisite-checks)
3. [Start Infrastructure](#3-start-infrastructure)
4. [Start the Application](#4-start-the-application)
5. [Explore the API with Swagger UI](#5-explore-the-api-with-swagger-ui)
6. [Health & Monitoring Endpoints](#6-health--monitoring-endpoints)
7. [File Registry — Upload & Track Files](#7-file-registry--upload--track-files)
8. [Reconciliation Results — Query Breaks & Matches](#8-reconciliation-results--query-breaks--matches)
9. [Resolve a Break](#9-resolve-a-break)
10. [Reconciliation Daily Summary](#10-reconciliation-daily-summary)
11. [Audit History for a Result](#11-audit-history-for-a-result)
12. [End-to-End Pipeline Test](#12-end-to-end-pipeline-test)
13. [Database Exploration](#13-database-exploration)
14. [Kafka Topic Inspection](#14-kafka-topic-inspection)
15. [Common Error Responses](#15-common-error-responses)
16. [Learning Path — What to Read Next](#16-learning-path--what-to-read-next)

---

## 1. Understanding the Architecture

Before making any API calls, understand what each module does:

```
┌─────────────────────────────────────────────────────────────┐
│                        recon-api                            │
│   REST Controllers  ·  Security  ·  OpenAPI  ·  Actuator   │
└────────────────────────────┬────────────────────────────────┘
                             │ calls
          ┌──────────────────┼──────────────────┐
          ▼                  ▼                  ▼
   recon-storage      recon-ingestion    recon-processing
   JPA Entities       File Watcher       Spring Batch Jobs
   Repositories       Kafka Producer     Validation
   Flyway Migrations  File Parsers       Matching Engine
          │                  │                  │
          └──────────────────┼──────────────────┘
                             ▼
                    recon-notification
                    Email / Slack / JIRA
```

### Data Flow (how a file becomes a result)

```
1. Drop .dat/.csv/.txt file → data/landing/
2. LocalFileWatcherService detects it (NIO2 WatchService)
3. File registered in recon_file_registry table
4. FileArrivedEvent published to Kafka topic: recon.file.arrived
5. FlatFileParserService parses & validates each record
6. Records written to recon_staging table
7. Spring Batch job reads staging → ValidationItemProcessor
8. ReconMatchingProcessor groups records by (date, entity, RCON code)
9. MATCHED / BREAK / UNMATCHED / PARTIAL results → recon_results table
10. Results available via REST API
```

### Indian Payment Source Systems

| Source System | Operator | Channel Type | File Format | Settlement |
|---|---|---|---|---|
| `UPI` | NPCI | Real-time P2P/P2M | `.dat` pipe-delimited | Continuous 24x7 |
| `IMPS` | NPCI | Real-time bank-to-bank | `.dat` pipe-delimited | Continuous 24x7 |
| `NEFT` | RBI | Batch transfers | `.csv` | Half-hourly batches |
| `RTGS` | RBI | High-value gross settlement (≥₹2L) | `.dat` pipe-delimited | Real-time gross |

### Indian Payment RCON Codes

| RCON Code | Channel | Type | Tolerance |
|---|---|---|---|
| `RECON_UPI_CR` | UPI | Credit settlements | ₹1.00 (paise rounding) |
| `RECON_UPI_DR` | UPI | Debit settlements | ₹1.00 (paise rounding) |
| `RECON_UPI_REV` | UPI | Reversals / refunds | ₹0.00 (exact) |
| `RECON_UPI_CHB` | UPI | Chargebacks | ₹0.00 (exact) |
| `RECON_IMPS_CR` | IMPS | Inward credits | ₹0.00 (exact) |
| `RECON_IMPS_DR` | IMPS | Outward debits | ₹0.00 (exact) |
| `RECON_IMPS_RET` | IMPS | Returns | ₹0.00 (exact) |
| `RECON_NEFT_CR` | NEFT | Batch inward credits | ₹0.00 (exact) |
| `RECON_NEFT_DR` | NEFT | Batch outward debits | ₹0.00 (exact) |
| `RECON_NEFT_RET` | NEFT | Returns | ₹0.00 (exact) |
| `RECON_RTGS_CR` | RTGS | Gross inward credits | ₹0.00 (exact) |
| `RECON_RTGS_DR` | RTGS | Gross outward debits | ₹0.00 (exact) |
| `RECON_RTGS_REJ` | RTGS | Rejected transactions | ₹0.00 (exact) |

### Match Statuses

| Entity | Table | Purpose |
|---|---|---|
| `ReconStaging` | `recon_staging` | Raw parsed records waiting to be matched |
| `ReconResult` | `recon_results` | Final match outcome per RCON code per day |
| `ReconFileRegistry` | `recon_file_registry` | Tracks every file received and its status |
| `RconToleranceConfig` | `rcon_tolerance_config` | Per-RCON-code tolerance thresholds |

### Match Statuses

| Status | Meaning |
|---|---|
| `MATCHED` | Both source systems agree (within tolerance) |
| `BREAK` | Difference exceeds tolerance — needs investigation |
| `UNMATCHED` | Record exists in one source only |
| `PARTIAL` | Only one of two expected sources provided data |

---

## 2. Prerequisite Checks

Open a PowerShell terminal and verify everything is installed:

```powershell
# Java 25
java -version
# Expected: openjdk version "25.x.x" or similar

# Maven wrapper
.\mvnw.cmd --version
# Expected: Apache Maven 3.9.x

# Docker
docker info | Select-Object -First 3
# Expected: Client, Server info lines

# Docker Compose
docker compose version
# Expected: Docker Compose version v2.x.x
```

If any command fails, install the missing tool before continuing.

---

## 3. Start Infrastructure

```powershell
cd C:\googlecloud_work\hdfc_jobserver\recon_app

# Start PostgreSQL 17 + Kafka 4.2.0
docker compose up -d postgres kafka

# Wait ~10 seconds then verify both are running
docker compose ps
```

Expected output:
```
NAME                    STATUS          PORTS
recon_app-kafka-1       running         0.0.0.0:9092->9092/tcp
recon_app-postgres-1    running         0.0.0.0:5432->5432/tcp
```

**Verify PostgreSQL:**
```powershell
docker compose exec postgres psql -U recon_user -d recondb -c "SELECT version();"
```

**Verify Kafka:**
```powershell
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh `
  --bootstrap-server localhost:9092 --list
```

---

## 4. Start the Application

```powershell
# Create the landing zone directory first
New-Item -ItemType Directory -Force -Path ".\data\landing"

# Build (skip tests for speed)
.\mvnw.cmd clean package -DskipTests

# Start the application
.\mvnw.cmd -pl recon-api spring-boot:run
```

Watch the startup log. You should see:
```
Found 4 JPA repository interfaces.
HikariPool-1 - Start completed.
Initialized JPA EntityManagerFactory for persistence unit 'default'
Tomcat started on port 8080 (http)
Started ReconApiApplication
```

If you see `Landing path \data\landing not found, watcher idle` — create the directory above and restart.

---

## 5. Explore the API with Swagger UI

Open your browser and go to:

```
http://localhost:8080/swagger-ui/index.html
```

You will see two controller groups:

| Controller | Base Path | What it does |
|---|---|---|
| `recon-result-controller` | `/api/v1/recon` | Query results, resolve breaks, summaries |
| `file-registry-controller` | `/api/v1/files` | Track file processing status |

> **Tip:** Click any endpoint → click **Try it out** → fill in the parameters → click **Execute**  
> This is the fastest way to explore the API without writing any code.

---

## 6. Health & Monitoring Endpoints

These endpoints are always available and require no data. Start here to confirm the app is running correctly.

### 6.1 Health Check

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health | ConvertTo-Json
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

**What to learn:** Spring Boot Actuator auto-configures health indicators. The `db` indicator confirms Flyway ran and PostgreSQL is reachable.

### 6.2 Application Info

```powershell
Invoke-RestMethod http://localhost:8080/actuator/info | ConvertTo-Json
```

### 6.3 Prometheus Metrics

```powershell
Invoke-RestMethod http://localhost:8080/actuator/prometheus | Select-String "recon|jvm_memory|http_server"
```

Look for custom metrics like:
- `recon_files_processed_total` — total files ingested
- `recon_breaks_total` — total breaks detected
- `http_server_requests_seconds` — HTTP latency histograms

**What to learn:** Micrometer automatically instruments Spring MVC, JPA, and HikariCP. Custom business metrics are added in `ReconMetricsService`.

### 6.4 All Available Actuator Endpoints

```powershell
Invoke-RestMethod http://localhost:8080/actuator | ConvertTo-Json -Depth 3
```

---

## 7. File Registry — Upload & Track Files

### 7.1 List Files for a Date and Source System

Before dropping any files, query the registry (it will return an empty list):

```powershell
# UPI files for today
Invoke-RestMethod "http://localhost:8080/api/v1/files?reportDate=2026-04-29&sourceSystem=UPI" | ConvertTo-Json

# NEFT files
Invoke-RestMethod "http://localhost:8080/api/v1/files?reportDate=2026-04-29&sourceSystem=NEFT" | ConvertTo-Json
```

**Source system values:** `UPI`, `IMPS`, `NEFT`, `RTGS`

### 7.2 Drop Indian Payment Sample Files

```powershell
# UPI — NPCI switch reconciliation file (pipe-delimited .dat)
Copy-Item "recon-ingestion\src\test\resources\sample\UPI_RECON_20260429_093000_001.dat" "data\landing\"

# Wait 5 seconds then check
Invoke-RestMethod "http://localhost:8080/api/v1/files?reportDate=2026-04-29&sourceSystem=UPI" | ConvertTo-Json
```

The UPI sample file contains:
```
HDR|UPI_20260429_001|UPI|20260429|HDFC0001|6|28750000.00|1.0
DTL|UPI000001|20260429|HDFC0001|...|RECON_UPI_CR|15000000.00|CR|INR|...|VPA:9876543210@hdfcbank|
DTL|UPI000002|20260429|HDFC0001|...|RECON_UPI_DR|8500000.00|DR|INR|...|VPA:user123@okaxis|
DTL|UPI000003|20260429|HDFC0001|...|RECON_UPI_REV|250000.00|CR|INR|...|VPA:refund@hdfcbank|
...
TRL|6|28750000.00|COMPLETE
```

Key fields in a UPI record:
- `VPA` — Virtual Payment Address (e.g. `9876543210@hdfcbank`)
- `pay_*` — NPCI UPI transaction reference (source_ref field)
- `CR/DR` — credit to bank account / debit from bank account

```powershell
# IMPS — NPCI bank-to-bank instant transfer file
Copy-Item "recon-ingestion\src\test\resources\sample\IMPS_RECON_20260429_090000_001.dat" "data\landing\"

# NEFT — RBI batch settlement CSV (different format — comma-separated)
Copy-Item "recon-ingestion\src\test\resources\sample\NEFT_RECON_20260429_120000_001.csv" "data\landing\"

# RTGS — RBI high-value gross settlement file (≥₹2 Lakh per transaction)
Copy-Item "recon-ingestion\src\test\resources\sample\RTGS_RECON_20260429_110000_001.dat" "data\landing\"
```

After dropping all 4 files wait 10–15 seconds then verify all channels:

```powershell
@("UPI","IMPS","NEFT","RTGS") | ForEach-Object {
    Write-Host "--- $_ ---"
    Invoke-RestMethod "http://localhost:8080/api/v1/files?reportDate=2026-04-29&sourceSystem=$_" | ConvertTo-Json
}
```

### 7.3 Get a Single File's Status

```powershell
Invoke-RestMethod `
  "http://localhost:8080/api/v1/files/SRCA_20260428_001/status" `
  | ConvertTo-Json
```

### 7.4 Try a Malformed File

```powershell
Copy-Item `
  "recon-ingestion\src\test\resources\sample\SRCA_RECON_MALFORMED.dat" `
  "data\landing\"
```

After processing, query the file status — it should show `FAILED` or `PARTIAL` with `errorCount > 0`.

### 7.5 Try a Control-Total Mismatch File

```powershell
Copy-Item `
  "recon-ingestion\src\test\resources\sample\SRCA_RECON_BAD_TOTAL.dat" `
  "data\landing\"
```

The HDR declares a total of `999999.00` but the DTL actual total is `5000.00`. The file will be rejected with `ControlTotalMismatchException`.

### 7.6 Reprocess a Failed File

```powershell
Invoke-RestMethod -Method Post `
  "http://localhost:8080/api/v1/files/SRCA_20260428_001/reprocess"
```

---

## 8. Reconciliation Results — Query Breaks & Matches

### 8.1 Get All Results for a Date and Entity

```powershell
# All matched UPI records for today
Invoke-RestMethod `
  "http://localhost:8080/api/v1/recon/results?reportDate=2026-04-29&entityId=HDFC0001&status=MATCHED&severity=LOW&page=0&size=20" `
  | ConvertTo-Json -Depth 5
```

**Try all statuses:**

```powershell
# UPI breaks (paise rounding difference > ₹1.00)
Invoke-RestMethod "http://localhost:8080/api/v1/recon/results?reportDate=2026-04-29&entityId=HDFC0001&status=BREAK&severity=HIGH" | ConvertTo-Json

# NEFT unmatched (RBI batch file arrived, CBS file not yet received)
Invoke-RestMethod "http://localhost:8080/api/v1/recon/results?reportDate=2026-04-29&entityId=HDFC0001&status=UNMATCHED&severity=MEDIUM" | ConvertTo-Json

# RTGS partial (only one side of high-value settlement arrived)
Invoke-RestMethod "http://localhost:8080/api/v1/recon/results?reportDate=2026-04-29&entityId=HDFC0001&status=PARTIAL&severity=HIGH" | ConvertTo-Json
```

**Example response (UPI credit matched):**
```json
{
  "content": [
    {
      "reconId": "RCON-HDFC0001-20260429-RECON_UPI_CR",
      "reportDate": "2026-04-29",
      "entityId": "HDFC0001",
      "rconCode": "RECON_UPI_CR",
      "sourceSystemA": "UPI",
      "balanceA": 15000000.00,
      "sourceSystemB": "IMPS",
      "balanceB": 15000000.00,
      "tolerance": 1.00,
      "matchStatus": "MATCHED",
      "severity": null,
      "currency": "INR",
      "resolved": false
    }
  ],
  "totalElements": 6,
  "totalPages": 1
}
```

### 8.2 Get a Single Result by Recon ID

```powershell
Invoke-RestMethod `
  "http://localhost:8080/api/v1/recon/results/RCON-E001-20260428-RCON0010" `
  | ConvertTo-Json -Depth 3
```

**What to learn:** `reconId` is the business key — composed of `entityId + reportDate + rconCode`. This is how operations teams look up a specific position.

---

## 9. Resolve a Break

When a break is investigated and explained, it is marked as resolved.

**Common Indian payment break scenarios:**

```powershell
# UPI paise rounding break — confirmed acceptable
Invoke-RestMethod -Method Patch `
  -Uri "http://localhost:8080/api/v1/recon/results/RCON-HDFC0001-20260429-RECON_UPI_CR/resolve" `
  -ContentType "application/json" `
  -Body '{"resolutionNote": "Paise rounding difference of ₹0.50 on UPI credit batch. Within NPCI accepted tolerance. Waived per Ops Manager approval #UPI-2026-0429."}'

# NEFT amount mismatch — timing difference in RBI batch
Invoke-RestMethod -Method Patch `
  -Uri "http://localhost:8080/api/v1/recon/results/RCON-HDFC0001-20260429-RECON_NEFT_DR/resolve" `
  -ContentType "application/json" `
  -Body '{"resolutionNote": "NEFT batch 3 (16:00 cut-off) posted to CBS at 16:32 — after RBI settlement report generated. Difference ₹92,50,000 will auto-clear in next batch cycle."}'

# RTGS rejection — beneficiary account frozen
Invoke-RestMethod -Method Patch `
  -Uri "http://localhost:8080/api/v1/recon/results/RCON-HDFC0001-20260429-RECON_RTGS_REJ/resolve" `
  -ContentType "application/json" `
  -Body '{"resolutionNote": "RTGS transaction RBI2026294567890004 rejected — beneficiary account frozen per RBI directive. Amount ₹0. No financial impact. JIRA: RTGS-2026-0429-001."}'
```

---

## 10. Reconciliation Daily Summary

Get aggregate statistics for a full business day:

```powershell
Invoke-RestMethod `
  "http://localhost:8080/api/v1/recon/summary?reportDate=2026-04-28" `
  | ConvertTo-Json
```

Expected response:
```json
{
  "reportDate": "2026-04-28",
  "totalRecords": 5,
  "matched": 3,
  "breaks": 1,
  "unmatched": 1,
  "partial": 0,
  "totalBreakAmount": 87500000.00,
  "criticalBreaks": 0,
  "highBreaks": 1
}
```

**What to learn:** This is what an Operations Manager would look at first thing every morning. The summary drives the daily reconciliation dashboard.

---

## 11. Audit History for a Result

Every time a result is touched (created, resolved, re-opened), an audit record is written:

```powershell
Invoke-RestMethod `
  "http://localhost:8080/api/v1/recon/results/RCON-E001-20260428-RCON2122/history" `
  | ConvertTo-Json -Depth 3
```

Expected response:
```json
[
  {
    "eventType": "CREATED",
    "matchStatus": "BREAK",
    "changedBy": "system",
    "changedAt": "2026-04-29T02:34:40Z",
    "note": null
  },
  {
    "eventType": "RESOLVED",
    "matchStatus": "BREAK",
    "changedBy": "system",
    "changedAt": "2026-04-29T02:35:10Z",
    "note": "Timing difference — loan booked after cut-off."
  }
]
```

---

## 12. End-to-End Pipeline Test

This section walks you through a complete reconciliation cycle using the CSV format:

### Step 1 — Drop a CSV file

```powershell
Copy-Item `
  "recon-ingestion\src\test\resources\sample\sample.csv" `
  "data\landing\LOANS_RECON_20260429_001.csv"
```

The CSV format:
```csv
fileId,recordId,reportDate,entityId,rconCode,balance,drCrInd,currency,sourceRef,comments
F-CSV-1,REC-1,2026-04-29,E001,RCON0010,1000.00,CR,USD,TXN-1,ok
```

### Step 2 — Wait for processing (5–10 seconds), then verify file was registered

```powershell
Invoke-RestMethod `
  "http://localhost:8080/api/v1/files?reportDate=2026-04-29&sourceSystem=LOANS_SYS" `
  | ConvertTo-Json
```

### Step 3 — Query the result

```powershell
Invoke-RestMethod `
  "http://localhost:8080/api/v1/recon/results?reportDate=2026-04-29&entityId=E001&status=UNMATCHED&severity=LOW" `
  | ConvertTo-Json -Depth 3
```

The record will be `UNMATCHED` because only one source (LOANS_SYS) provided data for `RCON0010` on `2026-04-29`. There is no corresponding CORE_BANKING record.

### Step 4 — Get the daily summary

```powershell
Invoke-RestMethod `
  "http://localhost:8080/api/v1/recon/summary?reportDate=2026-04-29" `
  | ConvertTo-Json
```

### Step 5 — Resolve the unmatched record

```powershell
$reconId = "RCON-E001-20260429-RCON0010"
Invoke-RestMethod -Method Patch `
  -Uri "http://localhost:8080/api/v1/recon/results/$reconId/resolve" `
  -ContentType "application/json" `
  -Body '{"resolutionNote":"Confirmed: CORE_BANKING file for this date not yet received."}'
```

### Step 6 — Verify audit trail

```powershell
Invoke-RestMethod `
  "http://localhost:8080/api/v1/recon/results/$reconId/history" `
  | ConvertTo-Json -Depth 3
```

---

## 13. Database Exploration

Connect directly to PostgreSQL to see what the API is reading from:

```powershell
docker compose exec postgres psql -U recon_user -d recondb
```

**Useful queries:**

```sql
-- What files have been processed?
SELECT file_id, file_name, source_system, file_status, total_records, error_count, received_at
FROM recon_file_registry
ORDER BY received_at DESC;

-- What does the raw staging data look like?
SELECT record_id, source_system, report_date, entity_id, rcon_code, balance, dr_cr_ind
FROM recon_staging
ORDER BY loaded_at DESC
LIMIT 20;

-- What are all the reconciliation results?
SELECT recon_id, rcon_code, match_status, balance_a, balance_b, severity, resolved
FROM recon_results
ORDER BY created_at DESC;

-- How many breaks by severity?
SELECT severity, COUNT(*) AS break_count
FROM recon_results
WHERE match_status = 'BREAK'
GROUP BY severity
ORDER BY break_count DESC;

-- What are the tolerance configurations?
SELECT rcon_code, tolerance_amount, effective_from
FROM rcon_tolerance_config
ORDER BY rcon_code;

-- Flyway migration history
SELECT version, description, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

Exit psql: `\q`

---

## 14. Kafka Topic Inspection

See the events flowing through the system in real time:

```powershell
# List all topics created by the application
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh `
  --bootstrap-server localhost:9092 --list
```

Expected topics:
- `recon.file.arrived` — published when a file lands
- `recon.processing.completed` — published when batch job finishes
- `recon.break.alerts` — published when a CRITICAL break is detected

**Tail the file-arrived topic:**
```powershell
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh `
  --bootstrap-server localhost:9092 `
  --topic recon.file.arrived `
  --from-beginning
```

Drop another file and watch the JSON event appear in the console.

**Example event:**
```json
{
  "fileId": "SRCA_20260428_001",
  "fileName": "SRCA_RECON_20260428_093000_001.dat",
  "sourceSystem": "CORE_BANKING",
  "reportDate": "2026-04-28",
  "filePath": "/data/landing/SRCA_RECON_20260428_093000_001.dat",
  "receivedAt": "2026-04-29T02:34:33Z"
}
```

Press `Ctrl+C` to stop tailing.

---

## 15. Common Error Responses

The API uses **RFC 9457 Problem Details** format for all errors:

```json
{
  "type": "https://recon.example.com/errors/not-found",
  "title": "Resource Not Found",
  "status": 404,
  "detail": "Recon result RCON-E001-20260428-RCON9999 not found",
  "instance": "/api/v1/recon/results/RCON-E001-20260428-RCON9999"
}
```

**Common errors to test:**

```powershell
# 404 — result not found
Invoke-RestMethod "http://localhost:8080/api/v1/recon/results/DOES-NOT-EXIST"

# 400 — missing required parameter
Invoke-RestMethod "http://localhost:8080/api/v1/recon/results"

# 400 — invalid date format
Invoke-RestMethod "http://localhost:8080/api/v1/recon/results?reportDate=not-a-date&entityId=E001&status=BREAK&severity=HIGH"

# 404 — file not found
Invoke-RestMethod "http://localhost:8080/api/v1/files/NOSUCHFILE/status"
```

---

## 16. Learning Path — What to Read Next

Work through the codebase in this order to understand how everything connects:

### 🟢 Start Here — Data Model

| File | What you'll learn |
|---|---|
| `recon-storage/src/main/java/com/recon/storage/entity/ReconStaging.java` | Raw record structure from file parsing |
| `recon-storage/src/main/java/com/recon/storage/entity/ReconResult.java` | Match outcome structure |
| `recon-storage/src/main/java/com/recon/storage/entity/ReconFileRegistry.java` | File lifecycle tracking |
| `recon-storage/src/main/resources/db/migration/` | All 5 Flyway SQL scripts — the exact schema |

### 🟡 File Ingestion Pipeline

| File | What you'll learn |
|---|---|
| `recon-ingestion/src/main/java/com/recon/ingestion/watcher/LocalFileWatcherService.java` | NIO2 WatchService + virtual threads |
| `recon-ingestion/src/main/java/com/recon/ingestion/parser/FlatFileParserService.java` | Pipe-delimited, CSV, fixed-width parsing with control total validation |
| `recon-ingestion/src/main/java/com/recon/ingestion/kafka/FileEventProducer.java` | How events flow to Kafka |
| `recon-ingestion/src/test/` | Unit tests showing how to test each parser format |

### 🟠 Processing & Matching

| File | What you'll learn |
|---|---|
| `recon-processing/src/main/java/com/recon/processing/processor/ValidationItemProcessor.java` | Bean Validation + Bloom filter duplicate detection |
| `recon-processing/src/main/java/com/recon/processing/processor/ReconMatchingProcessor.java` | Matching algorithm — MATCHED / BREAK / UNMATCHED / PARTIAL logic |
| `recon-processing/src/test/` | Unit tests showing all 5 matching scenarios |

### 🔵 REST API Layer

| File | What you'll learn |
|---|---|
| `recon-api/src/main/java/com/recon/api/controller/ReconResultController.java` | All result endpoints |
| `recon-api/src/main/java/com/recon/api/controller/FileRegistryController.java` | All file endpoints |
| `recon-api/src/main/java/com/recon/api/controller/GlobalExceptionHandler.java` | RFC 9457 error handling |
| `recon-api/src/main/java/com/recon/api/config/SecurityConfig.java` | Spring Security — currently permits all |
| `recon-api/src/main/resources/application.yml` | All configuration properties |

### 🔴 Advanced Topics

| File | What you'll learn |
|---|---|
| `recon-notification/src/main/java/com/recon/notification/service/NotificationServiceImpl.java` | Email + Slack alerting for CRITICAL breaks |
| `recon-ingestion/src/main/java/com/recon/ingestion/config/KafkaConfig.java` | Kafka producer + consumer configuration |
| `recon-api/src/main/java/com/recon/api/config/JpaConfig.java` | Cross-module entity scanning |
| `recon-api/src/test/java/com/recon/api/integration/ReconPipelineIntegrationTest.java` | Full end-to-end test with Testcontainers |

---

## Quick Reference Card

```powershell
# ── Infrastructure ──────────────────────────────────────────────
docker compose up -d postgres kafka                        # start services
docker compose ps                                          # check status
docker compose down                                        # stop all

# ── Application ─────────────────────────────────────────────────
.\mvnw.cmd clean package -DskipTests                      # build
.\mvnw.cmd -pl recon-api spring-boot:run                  # run
.\mvnw.cmd test                                            # run all tests

# ── Health ──────────────────────────────────────────────────────
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/info

# ── Swagger ─────────────────────────────────────────────────────
start http://localhost:8080/swagger-ui/index.html

# ── Drop Indian payment files ────────────────────────────────────
Copy-Item "recon-ingestion\src\test\resources\sample\UPI_RECON_20260429_093000_001.dat"  "data\landing\"
Copy-Item "recon-ingestion\src\test\resources\sample\IMPS_RECON_20260429_090000_001.dat" "data\landing\"
Copy-Item "recon-ingestion\src\test\resources\sample\NEFT_RECON_20260429_120000_001.csv" "data\landing\"
Copy-Item "recon-ingestion\src\test\resources\sample\RTGS_RECON_20260429_110000_001.dat" "data\landing\"

# ── Files ───────────────────────────────────────────────────────
# sourceSystem: UPI | IMPS | NEFT | RTGS
curl "http://localhost:8080/api/v1/files?reportDate=2026-04-29&sourceSystem=UPI"
curl "http://localhost:8080/api/v1/files?reportDate=2026-04-29&sourceSystem=NEFT"

# ── Results ─────────────────────────────────────────────────────
# entityId: HDFC0001   status: MATCHED|BREAK|UNMATCHED|PARTIAL   severity: LOW|MEDIUM|HIGH|CRITICAL
curl "http://localhost:8080/api/v1/recon/results?reportDate=2026-04-29&entityId=HDFC0001&status=MATCHED&severity=LOW"
curl "http://localhost:8080/api/v1/recon/results?reportDate=2026-04-29&entityId=HDFC0001&status=BREAK&severity=HIGH"
curl "http://localhost:8080/api/v1/recon/summary?reportDate=2026-04-29"

# ── Resolve a break (INR context) ───────────────────────────────
curl -X PATCH http://localhost:8080/api/v1/recon/results/{reconId}/resolve \
  -H "Content-Type: application/json" \
  -d '{"resolutionNote":"NEFT batch timing difference. Will clear in next cycle."}'

# ── Database ────────────────────────────────────────────────────
docker compose exec postgres psql -U recon_user -d recondb

# ── Kafka ───────────────────────────────────────────────────────
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic recon.file.arrived --from-beginning

# ── RCON codes in DB ─────────────────────────────────────────────
# docker compose exec postgres psql -U recon_user -d recondb -c "SELECT rcon_code, tolerance FROM rcon_tolerance_config ORDER BY rcon_code;"
# RECON_IMPS_CR  0.00  |  RECON_IMPS_DR  0.00  |  RECON_IMPS_RET  0.00
# RECON_NEFT_CR  0.00  |  RECON_NEFT_DR  0.00  |  RECON_NEFT_RET  0.00
# RECON_RTGS_CR  0.00  |  RECON_RTGS_DR  0.00  |  RECON_RTGS_REJ  0.00
# RECON_UPI_CR   1.00  |  RECON_UPI_DR   1.00  |  RECON_UPI_CHB   0.00  |  RECON_UPI_REV  0.00
```

