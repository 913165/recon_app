# RCON Reconciliation System — Step-by-Step Code Walkthrough

> **Purpose:** Walk through the actual source code layer by layer, explaining *what* each class does,
> *why* it was written that way, and *how* it connects to the next layer.  
> **Audience:** Developers learning the codebase for the first time.  
> **Tip:** Open each file in your IDE as you read the section about it.

---

## Table of Contents

1. [Module Map — Where Everything Lives](#1-module-map--where-everything-lives)
2. [Layer 0 — Common DTOs & Enums](#2-layer-0--common-dtos--enums-recon-common)
3. [Layer 1 — Database Schema (Flyway Migrations)](#3-layer-1--database-schema-flyway-migrations-recon-storage)
4. [Layer 2 — JPA Entities & Repositories](#4-layer-2--jpa-entities--repositories-recon-storage)
5. [Layer 3 — File Watcher (How Files Enter the System)](#5-layer-3--file-watcher-recon-ingestion)
6. [Layer 4 — File Parser (How Records Are Extracted)](#6-layer-4--file-parser-recon-ingestion)
7. [Layer 5 — Kafka Producer (How Events Flow)](#7-layer-5--kafka-producer-recon-ingestion)
8. [Layer 6 — Validation Processor](#8-layer-6--validation-processor-recon-processing)
9. [Layer 7 — Matching Engine (The Core Business Logic)](#9-layer-7--matching-engine-recon-processing)
10. [Layer 8 — REST Controllers](#10-layer-8--rest-controllers-recon-api)
11. [Layer 9 — Service Layer](#11-layer-9--service-layer-recon-api)
12. [Layer 10 — Notifications](#12-layer-10--notifications-recon-notification)
13. [Configuration Deep Dive](#13-configuration-deep-dive)
14. [Test Code Walkthrough](#14-test-code-walkthrough)
15. [Full Request Trace — File to API Response](#15-full-request-trace--file-to-api-response)

---

## 1. Module Map — Where Everything Lives

```
recon-system/                        ← parent POM only (no code)
├── recon-common/                    ← shared records, enums, exceptions
│   └── com.recon.common/
│       ├── dto/                     ← Java Records (immutable data carriers)
│       ├── enums/                   ← MatchStatus, SourceSystem, Severity, etc.
│       └── exception/               ← ControlTotalMismatchException, DuplicateFileException
│
├── recon-storage/                   ← database layer
│   └── com.recon.storage/
│       ├── entity/                  ← JPA @Entity classes (tables)
│       ├── repository/              ← Spring Data JPA interfaces
│       ├── service/                 ← FileRegistryService, BulkLoadService
│       └── resources/db/migration/  ← V1..V5 Flyway SQL scripts
│
├── recon-ingestion/                 ← file detection and parsing
│   └── com.recon.ingestion/
│       ├── watcher/                 ← LocalFileWatcherService (NIO2)
│       ├── parser/                  ← FlatFileParserService (.dat/.csv/.txt)
│       ├── kafka/                   ← FileEventProducer
│       └── config/                  ← KafkaConfig (producer + consumer beans)
│
├── recon-processing/                ← validation and matching
│   └── com.recon.processing/
│       ├── processor/               ← ValidationItemProcessor, ReconMatchingProcessor
│       └── config/                  ← Spring Batch job configuration
│
├── recon-notification/              ← alerting
│   └── com.recon.notification/
│       └── service/                 ← NotificationServiceImpl, NoOpNotificationService
│
└── recon-api/                       ← HTTP entry point (the runnable jar)
    └── com.recon.api/
        ├── controller/              ← ReconResultController, FileRegistryController
        ├── service/                 ← ReconResultServiceImpl
        ├── mapper/                  ← ReconResultMapper (Entity → DTO)
        └── config/                  ← JpaConfig, KafkaConfig, SecurityConfig, WebConfig
```

**Dependency direction:** `recon-api` → `recon-processing`, `recon-ingestion`, `recon-notification` → `recon-storage` → `recon-common`

No module depends on `recon-api`. This means the core business logic can be tested without starting a web server.

---

## 2. Layer 0 — Common DTOs & Enums (`recon-common`)

### Why a separate module?

Every other module needs the same `MatchStatus` enum and the same `FileArrivedEvent` record. Putting them in `recon-common` means no circular dependencies.

### 📄 Key Enums

**`SourceSystem.java`**
```java
// recon-common/src/main/java/com/recon/common/enums/SourceSystem.java
public enum SourceSystem {
    CORE_BANKING,   // Primary source — the bank's ledger
    LOANS_SYS,      // Loan origination system
    TRADING_GL      // Trading general ledger
}
```
> 🔑 **Why it matters:** The matching engine uses this to identify which side of the comparison each record belongs to. `CORE_BANKING` is always the baseline (side A).

**`MatchStatus.java`**
```java
public enum MatchStatus {
    MATCHED,    // Both sides agree (within tolerance)
    BREAK,      // Difference exceeds tolerance
    UNMATCHED,  // Record exists in one source only
    PARTIAL     // Only one source provided data for this period
}
```

**`Severity.java`**
```java
public enum Severity {
    LOW,      // difference < $1,000
    MEDIUM,   // difference $1,000–$9,999
    HIGH,     // difference $10,000–$99,999
    CRITICAL  // difference ≥ $100,000
}
```

### 📄 Key Records (DTOs)

**`FileArrivedEvent.java`** — the Kafka message published when a file lands:
```java
// Java Record = immutable, auto-equals/hashCode/toString, no boilerplate
public record FileArrivedEvent(
    String fileId,
    String fileName,
    SourceSystem sourceSystem,
    String filePath,
    LocalDate reportDate,
    OffsetDateTime receivedAt
) {}
```

**`ValidatedRecord.java`** — a record that passed validation, ready for matching:
```java
public record ValidatedRecord(
    String fileId,
    SourceSystem sourceSystem,
    String recordId,
    LocalDate reportDate,
    String entityId,
    String rconCode,
    BigDecimal balance,
    String currency
) {}
```

> 🔑 **Why Records?** Java Records enforce immutability — a `ValidatedRecord` can never be partially modified after creation, which prevents subtle bugs in the matching engine.

---

## 3. Layer 1 — Database Schema (Flyway Migrations) (`recon-storage`)

**Path:** `recon-storage/src/main/resources/db/migration/`

Flyway runs these scripts automatically on startup **in version order**. You never need to run them manually.

### V1 — `recon_staging` table

```sql
-- File: V1__create_staging_table.sql
CREATE TABLE recon_staging (
    id          BIGSERIAL PRIMARY KEY,
    file_id     VARCHAR(50)   NOT NULL,   -- links back to recon_file_registry
    source_system VARCHAR(20) NOT NULL,   -- CORE_BANKING / LOANS_SYS / TRADING_GL
    record_id   VARCHAR(30)   NOT NULL,   -- unique ID per record within a file
    report_date DATE          NOT NULL,   -- the business date
    entity_id   VARCHAR(10)   NOT NULL,   -- bank branch or subsidiary (e.g. E001)
    rcon_code   VARCHAR(10)   NOT NULL,   -- FDIC RCON code (e.g. RCON0010)
    balance     NUMERIC(20,2) NOT NULL,
    dr_cr_ind   CHAR(2)       NOT NULL,   -- DR or CR
    processed   BOOLEAN       NOT NULL DEFAULT FALSE  -- has matching engine seen it?
);
-- Partial index — only unprocessed rows, keeps it small as rows are processed
CREATE INDEX idx_staging_processed ON recon_staging(processed) WHERE processed = FALSE;
```

> 🔑 **Why a staging table?** Files arrive at different times. CORE_BANKING arrives at 08:00, LOANS_SYS at 09:00. The staging table holds everything until both sides are present, then the matching job runs.

### V2 — `recon_results` table (partitioned)

```sql
-- File: V2__create_recon_results_table.sql
CREATE TABLE recon_results (
    id            BIGSERIAL,
    recon_id      VARCHAR(40)   NOT NULL,   -- business key: entityId-date-rconCode
    report_date   DATE          NOT NULL,
    -- ... all matching columns ...
    difference    NUMERIC(20,2) GENERATED ALWAYS AS (balance_a - balance_b) STORED,
    match_status  VARCHAR(15)   NOT NULL,   -- MATCHED/BREAK/UNMATCHED/PARTIAL
    resolved      BOOLEAN       NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id, report_date)
) PARTITION BY RANGE (report_date);

-- One partition per month = fast queries, easy archiving
CREATE TABLE recon_results_2026_04 PARTITION OF recon_results
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
```

> 🔑 **Why partitioning?** A bank runs reconciliation every business day. After 1 year that's ~250 days × thousands of RCON codes = millions of rows. Range partitioning by `report_date` means queries for "today's breaks" only scan one small partition, not the whole table.
>
> 🔑 **`GENERATED ALWAYS AS ... STORED`** — PostgreSQL computes `difference = balance_a - balance_b` automatically on insert/update. You never compute it in Java.

### V5 — Tolerance Configuration

```sql
-- File: V5__create_tolerance_table.sql
CREATE TABLE rcon_tolerance_config (
    rcon_code  VARCHAR(10) NOT NULL UNIQUE,
    tolerance  NUMERIC(10,2) NOT NULL DEFAULT 0.00
);

-- Seed data — different RCON codes have different tolerances
INSERT INTO rcon_tolerance_config(rcon_code, tolerance)
VALUES ('RCON0010', 0.00),    -- cash: zero tolerance
       ('RCON0071', 100.00),  -- interest-bearing balances: $100 tolerance
       ('RCON2122', 500.00),  -- total loans: $500 tolerance
       ('RCON2200', 500.00);  -- total deposits: $500 tolerance
```

> 🔑 **Why a table, not config file?** Tolerances change by regulatory requirement and business decision. Storing them in the database means they can be updated without a code deployment.

---

## 4. Layer 2 — JPA Entities & Repositories (`recon-storage`)

### 📄 `ReconStaging.java` — maps to `recon_staging`

```java
// recon-storage/src/main/java/com/recon/storage/entity/ReconStaging.java
@Entity
@Table(name = "recon_staging")
@Data           // Lombok: generates getters, setters, equals, hashCode, toString
@Builder        // Lombok: enables ReconStaging.builder().fileId("X").build()
@NoArgsConstructor
@AllArgsConstructor
public class ReconStaging {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // PostgreSQL BIGSERIAL
    private Long id;

    @Enumerated(EnumType.STRING)  // stores "CORE_BANKING" not "0"
    @Column(name = "source_system", nullable = false)
    private SourceSystem sourceSystem;

    @Column(name = "processed")
    @Builder.Default
    private Boolean processed = false;    // default false, set true after matching
    
    // ... other fields map 1:1 with V1 SQL columns
}
```

### 📄 `ReconResult.java` — maps to `recon_results`

```java
// recon-storage/src/main/java/com/recon/storage/entity/ReconResult.java
@Entity
@Table(name = "recon_results")
public class ReconResult {

    @Column(name = "recon_id", nullable = false, length = 40)
    private String reconId;        // e.g. "SRCA_20260428_001-SRCA000001"
    
    @Enumerated(EnumType.STRING)
    private MatchStatus matchStatus; // MATCHED / BREAK / UNMATCHED / PARTIAL

    @Nullable                       // JSpecify: documents that this CAN be null
    private BigDecimal balanceB;    // null when counterpart has no data

    @Builder.Default
    private BigDecimal tolerance = BigDecimal.ZERO;
    
    // NOTE: 'difference' column is GENERATED in DB — no Java field needed
}
```

### 📄 Repositories — zero boilerplate queries

```java
// recon-storage/src/main/java/com/recon/storage/repository/ReconResultRepository.java
public interface ReconResultRepository extends JpaRepository<ReconResult, Long> {

    // Spring Data derives this SQL from the method name:
    // SELECT * FROM recon_results WHERE report_date=? AND entity_id=? AND match_status=? AND severity=?
    Page<ReconResult> findByReportDateAndEntityIdAndMatchStatusAndSeverity(
        LocalDate reportDate, String entityId, MatchStatus status, Severity severity, Pageable pageable);

    Optional<ReconResult> findByReconId(String reconId);
    
    List<ReconResult> findByReportDateAndMatchStatus(LocalDate reportDate, MatchStatus status);
}
```

> 🔑 **No SQL written, no `@Query` needed.** Spring Data JPA reads the method name and generates the query at startup. If you rename a column in SQL and forget to rename the field, the app will fail to start with a clear error — caught early.

---

## 5. Layer 3 — File Watcher (`recon-ingestion`)

### 📄 `LocalFileWatcherService.java`

**How it starts:**
```java
// recon-ingestion/.../watcher/LocalFileWatcherService.java

@Service
@ConditionalOnProperty(name = "recon.landing-zone.mode", havingValue = "local", matchIfMissing = true)
public class LocalFileWatcherService implements FileWatcherService {

    @PostConstruct          // called automatically after Spring creates this bean
    void init() {
        // Java 21+ virtual thread — lightweight, doesn't block a platform thread
        Thread.ofVirtual().name("recon-file-watcher").start(this::startWatching);
    }
```

> 🔑 **`@ConditionalOnProperty`** — If you set `recon.landing-zone.mode=s3`, this bean is skipped entirely and `S3PollingWatcherService` activates instead. Swap file sources without changing business logic.

**The watch loop:**
```java
    public void startWatching() {
        Path dir = Path.of(landingPath);

        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            // Register for CREATE events only — we don't care about modifications
            dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE);

            while (true) {
                WatchKey key = watchService.take();    // blocks until a file arrives
                for (WatchEvent<?> event : key.pollEvents()) {
                    Path created = dir.resolve((Path) event.context());
                    handleFile(created);               // process each new file
                }
                key.reset();   // MUST reset or the WatchKey stops firing
            }
        }
    }
```

> 🔑 **`watchService.take()`** blocks the virtual thread (not a platform thread) with zero CPU usage. This is far more efficient than polling with `Thread.sleep(5000)`.

**File routing — naming convention:**
```java
    private SourceSystem resolveSourceSystem(String name) {
        if (name.startsWith("SRCA_")) return SourceSystem.CORE_BANKING;
        if (name.startsWith("SRCB_")) return SourceSystem.LOANS_SYS;
        return SourceSystem.TRADING_GL;   // SRCC_ or anything else
    }
```

> 🔑 **Convention over configuration.** The file prefix determines the source system. `SRCA_RECON_20260428_093000_001.dat` → CORE_BANKING, date = 2026-04-28. No database lookup needed.

**On file arrival:**
```java
    private void handleFile(Path created) {
        // 1. Build the event record
        FileArrivedEvent arrivedEvent = new FileArrivedEvent(
            UUID.randomUUID().toString(), name, sourceSystem, 
            created.toAbsolutePath().toString(), reportDate, OffsetDateTime.now()
        );

        // 2. Register in database FIRST (idempotency check happens here)
        fileRegistryService.register(arrivedEvent);

        // 3. Then publish to Kafka (consumer will trigger parsing)
        fileEventProducer.publishFileArrived(arrivedEvent);
    }
```

> 🔑 **Register before publish.** If the app crashes between the two calls, the file is in the registry as `RECEIVED` but no Kafka message was sent. On restart, a scheduled job can detect these orphaned files and re-publish.

---

## 6. Layer 4 — File Parser (`recon-ingestion`)

### 📄 `FlatFileParserService.java`

**Format dispatch — the entry point:**
```java
// recon-ingestion/.../parser/FlatFileParserService.java
public ParseResult parse(Path filePath, SourceSystem sourceSystem) {
    String name = filePath.getFileName().toString().toLowerCase();
    return switch (name.substring(name.lastIndexOf('.') + 1)) {
        case "dat"  -> parsePipeDelimited(filePath, sourceSystem);
        case "csv"  -> parseCsv(filePath, sourceSystem);
        default     -> parseFixedWidth(filePath, sourceSystem);   // .txt = EBCDIC fixed-width
    };
}
```

> 🔑 **Pattern: single entry point, branching internally.** Callers don't need to know which format they're dealing with. The file extension determines the parser.

### Pipe-delimited parsing with virtual-thread parallelism:

```java
private ParseResult parsePipeDelimited(Path filePath, SourceSystem sourceSystem) {
    List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
    
    String header  = lines.get(0);                     // HDR line
    String trailer = lines.get(lines.size() - 1);      // TRL line
    List<String> dtlLines = lines.subList(1, lines.size() - 1); // DTL lines

    // Parse detail records in a virtual-thread pool — each record independently
    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
        CompletableFuture<ParsedLines> dtlTask = CompletableFuture.supplyAsync(
            () -> parseDtlLines(dtlLines, sourceSystem), executor
        );
        ParsedLines parsed = dtlTask.join();
        
        // Validate AFTER parsing — compare trailer total to actual sum
        validateControlTotals(header, trailer, parsed.controlTotal());
        return new ParseResult(...);
    }
}
```

**What each line looks like:**
```
HDR|SRCA_20260428_001|CORE_BANKING|20260428|E001|5|135550000.00|1.0
↑   ↑                ↑            ↑        ↑   ↑  ↑
tag fileId           source       date    entity count  total

DTL|SRCA000001|20260428|E001|FIRST NAT BANK|1010|CASH AND DUE|RCON0010|5000000.00|CR|USD|093000|TXN-001|
↑   ↑          ↑        ↑    ↑              ↑    ↑            ↑        ↑          ↑  ↑
tag recordId  date    entity  entityName   acct  desc         rconCode  balance   DC  ccy

TRL|5|135550000.00|COMPLETE
↑   ↑  ↑
tag count  total (must match sum of DTL balances)
```

**Control total validation — prevents silent data loss:**
```java
private void validateControlTotals(String header, String trailer, BigDecimal actualTotal) {
    String[] hdr = header.split("\\|");
    BigDecimal expectedTotal = new BigDecimal(hdr[6]);   // from HDR
    BigDecimal trailerTotal  = new BigDecimal(trailer.split("\\|")[2]);

    // Both the header AND trailer must agree with the actual sum
    if (expectedTotal.compareTo(actualTotal) != 0) {
        throw new ControlTotalMismatchException(expectedTotal, actualTotal);
    }
}
```

> 🔑 **Why control totals matter:** If a file is truncated mid-transfer (network failure, disk full), you get 4 records instead of 5. Without control total validation, those 4 records would be silently matched — producing a phantom break on the 5th. Control totals are the first line of defence.

### CSV parsing with Apache Commons CSV:

```java
private ParseResult parseCsv(Path filePath, SourceSystem sourceSystem) {
    try (CSVParser parser = CSVParser.parse(filePath, StandardCharsets.UTF_8,
            CSVFormat.DEFAULT.builder()
                .setHeader()               // first row is the header
                .setSkipHeaderRecord(true)
                .build())) {

        parser.forEach(rec -> {
            try {
                ReconStagingDto dto = new ReconStagingDto(
                    rec.get("fileId"),           // access by column name, not index
                    sourceSystem,
                    rec.get("recordId"),
                    LocalDate.parse(rec.get("reportDate")),
                    // ...
                );
                success.add(dto);
            } catch (Exception ex) {
                errors.add(new ParseErrorRecord(rec.toString(), ex.getMessage()));
                // NOTE: errors don't abort parsing — all records are attempted
            }
        });
    }
}
```

> 🔑 **Errors are collected, not thrown.** A single bad row doesn't abort the file. All good rows are processed; bad rows go to the error log. The `ParseResult` contains both `successRecords` and `errorRecords`.

---

## 7. Layer 5 — Kafka Producer (`recon-ingestion`)

### 📄 `KafkaConfig.java` — producer + consumer beans

```java
// recon-ingestion/.../config/KafkaConfig.java
@Configuration
@EnableKafka
public class KafkaConfig {

    @Bean @Primary
    public ProducerFactory<String, FileArrivedEvent> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");            // wait for all replicas
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // no duplicate messages
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean @Primary
    public KafkaTemplate<String, FileArrivedEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

> 🔑 **`ENABLE_IDEMPOTENCE_CONFIG = true`** ensures each file event is delivered exactly once to Kafka, even if the network retries. Without this, a retry after a timeout could produce two events for the same file.

### 📄 `FileEventProducer.java`

```java
// recon-ingestion/.../kafka/FileEventProducer.java
@Service
@RequiredArgsConstructor
public class FileEventProducer {

    private final KafkaTemplate<String, FileArrivedEvent> kafkaTemplate;

    public void publishFileArrived(FileArrivedEvent event) {
        // Key = fileId: ensures all events for the same file go to the same partition
        kafkaTemplate.send(topic, event.fileId(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish event for file {}", event.fileId(), ex);
                } else {
                    log.info("Published file-arrived event for {}", event.fileId());
                }
            });
    }
}
```

> 🔑 **`send()` returns a `CompletableFuture`** — the call is non-blocking. The watcher thread doesn't wait for Kafka to acknowledge. The callback logs success/failure asynchronously.

---

## 8. Layer 6 — Validation Processor (`recon-processing`)

### 📄 `ValidationItemProcessor.java`

This class acts as the **gatekeeper** — every record must pass before reaching the matching engine.

```java
// recon-processing/.../processor/ValidationItemProcessor.java
@Component
public class ValidationItemProcessor {

    // Guava BloomFilter — probabilistic duplicate detection
    // Holds up to 5 million record IDs in ~4MB of RAM
    // False-positive rate: ~3% (a record might be wrongly flagged as dup)
    // False-negative rate: 0% (a real dup is NEVER missed)
    private final BloomFilter<CharSequence> dupFilter =
        BloomFilter.create(Funnels.stringFunnel(UTF_8), 5_000_000);

    private static final Pattern RCON_PATTERN = Pattern.compile("^RCON\\d{4}$");

    public ValidatedRecord process(ReconStaging item) {

        // Check 1: RCON code must match "RCON" + exactly 4 digits
        if (!RCON_PATTERN.matcher(item.getRconCode()).matches()) {
            throw new ValidationException("Invalid RCON code: " + item.getRconCode());
        }

        // Check 2: Balance cannot be negative (no negative positions)
        if (item.getBalance().signum() < 0) {
            throw new ValidationException("Balance cannot be negative");
        }

        // Check 3: Currency must be a valid ISO 4217 code
        if (!isIsoCurrency(item.getCurrency())) {
            throw new ValidationException("Invalid currency: " + item.getCurrency());
        }

        // Check 4: Bloom filter duplicate detection
        if (dupFilter.mightContain(item.getRecordId())) {
            throw new ValidationException("Duplicate record detected: " + item.getRecordId());
        }
        dupFilter.put(item.getRecordId());   // add to filter AFTER checking

        // Convert entity → validated record (safe, immutable)
        return new ValidatedRecord(
            item.getFileId(), item.getSourceSystem(), item.getRecordId(),
            item.getReportDate(), item.getEntityId(), item.getRconCode(),
            item.getBalance(), item.getCurrency()
        );
    }
}
```

**Why a Bloom Filter instead of a database query?**

| Approach | Speed | Memory | Accuracy |
|---|---|---|---|
| `SELECT COUNT(*) FROM recon_staging WHERE record_id=?` | ~1ms per record | 0 | 100% |
| Bloom Filter | ~100ns per record | ~4MB | 99.97% (3% false positives) |

For 1 million records: DB = ~16 minutes of queries. Bloom Filter = ~100ms. The 3% false-positive rate means ~30,000 records are incorrectly flagged as duplicates and need manual review — acceptable for a system that also validates by business key at the DB level.

---

## 9. Layer 7 — Matching Engine (`recon-processing`)

### 📄 `ReconMatchingProcessor.java` — the core business logic

**Step 1 — Group records by business key:**
```java
public List<ReconResult> processGroups(List<ValidatedRecord> records) {
    Map<String, List<ValidatedRecord>> grouped = new ConcurrentHashMap<>();
    
    // Key = "reportDate|entityId|rconCode"
    // e.g. "2026-04-28|E001|RCON0010"
    records.forEach(record ->
        grouped.computeIfAbsent(key(record), k -> new ArrayList<>()).add(record)
    );
```

> 🔑 **The grouping key is the reconciliation unit.** Two records with the same `reportDate + entityId + rconCode` but different `sourceSystem` are the two sides of the comparison. If both are CORE_BANKING for the same RCON code, it's a duplicate — the validation layer should have caught it.

**Step 2 — Identify sides within a group:**
```java
private ReconResult matchGroup(List<ValidatedRecord> group) {
    ValidatedRecord srcA = group.stream()
        .filter(r -> r.sourceSystem() == SourceSystem.CORE_BANKING).findFirst().orElse(null);
    ValidatedRecord srcB = group.stream()
        .filter(r -> r.sourceSystem() == SourceSystem.LOANS_SYS).findFirst().orElse(null);

    ValidatedRecord baseline   = srcA != null ? srcA : srcB;  // CORE_BANKING preferred as baseline
    ValidatedRecord counterpart = (baseline == srcA) ? srcB : srcA;  // other side (may be null)
    
    return buildResult(baseline, counterpart, tolerance);
}
```

**Step 3 — Determine match outcome:**
```java
private ReconResult buildResult(ValidatedRecord a, ValidatedRecord b, BigDecimal tolerance) {
    BigDecimal balanceA = a.balance();
    BigDecimal balanceB = b == null ? null : b.balance();
    BigDecimal difference = balanceB == null ? balanceA : balanceA.subtract(balanceB);

    MatchStatus status;
    if (b == null) {
        status = MatchStatus.UNMATCHED;             // one side missing entirely
    } else if (difference.abs().compareTo(tolerance) <= 0) {
        status = MatchStatus.MATCHED;               // within tolerance → clean
    } else {
        status = MatchStatus.BREAK;                 // exceeds tolerance → needs investigation
    }

    // Special case: PARTIAL = non-CORE_BANKING has data but CORE_BANKING doesn't
    if (b == null && a.sourceSystem() != SourceSystem.CORE_BANKING) {
        status = MatchStatus.PARTIAL;
    }

    Severity severity = toSeverity(difference.abs());
    // ...
}
```

**Step 4 — Severity classification:**
```java
private Severity toSeverity(BigDecimal absDifference) {
    if (absDifference.compareTo(BigDecimal.valueOf(1_000))    < 0) return Severity.LOW;
    if (absDifference.compareTo(BigDecimal.valueOf(10_000))   < 0) return Severity.MEDIUM;
    if (absDifference.compareTo(BigDecimal.valueOf(100_000))  < 0) return Severity.HIGH;
    return Severity.CRITICAL;  // ≥ $100,000 → immediate attention
}
```

**Step 5 — Parallel matching with virtual threads:**
```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    return grouped.values().stream()
        .map(group -> executor.submit(() -> matchGroup(group)))  // submit each group
        .map(future -> future.get())                             // collect results
        .toList();
}
```

> 🔑 **`newVirtualThreadPerTaskExecutor()`** — creates one virtual thread per group. If there are 10,000 RCON codes to match, 10,000 virtual threads run, each blocking independently on the tolerance DB lookup. Platform threads are not blocked.

**The tolerance lookup:**
```java
BigDecimal tolerance = toleranceConfigRepository.findByRconCode(baseline.rconCode())
    .map(config -> config.getTolerance())
    .orElse(BigDecimal.ZERO);  // if no config → zero tolerance (exact match required)
```

> 🔑 **Default to zero tolerance.** Unrecognised RCON codes are treated strictly. This means a new RCON code added to a file will immediately produce BREAKs if no tolerance is configured — a deliberate design to force explicit configuration.

---

## 10. Layer 8 — REST Controllers (`recon-api`)

### 📄 `ReconResultController.java`

```java
// recon-api/.../controller/ReconResultController.java
@RestController
@RequestMapping("/api/v1/recon")
@RequiredArgsConstructor
public class ReconResultController {

    private final ReconResultService reconResultService;

    @Operation(summary = "Get paged reconciliation results")  // Swagger documentation
    @GetMapping("/results")
    public Page<ReconResultDto> results(
        @RequestParam LocalDate reportDate,     // Spring auto-parses "2026-04-28"
        @RequestParam String entityId,
        @RequestParam MatchStatus status,       // Spring auto-converts "BREAK" → enum
        @RequestParam Severity severity,
        @RequestParam(defaultValue = "0")  int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return reconResultService.getResults(reportDate, entityId, status, severity, page, size);
    }

    @PatchMapping("/results/{reconId}/resolve")
    public ReconResultDto resolve(
        @PathVariable String reconId,
        @Valid ResolveRequest request           // @Valid triggers Bean Validation
    ) {
        return reconResultService.resolve(reconId, request.resolutionNote());
    }
}
```

> 🔑 **`@Valid ResolveRequest`** — if `resolutionNote` is blank or null, Spring returns `400 Bad Request` before the service method is ever called. Input validation at the boundary.

### 📄 `GlobalExceptionHandler.java` — RFC 9457 Problem Details

```java
// recon-api/.../controller/GlobalExceptionHandler.java
@ControllerAdvice   // intercepts exceptions from ALL controllers
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleNotFound(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("https://recon.example.com/errors/not-found"));
        pd.setTitle("Resource Not Found");
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        // Returns: {"status":400, "title":"Validation Failed", "detail":"resolutionNote: must not be blank"}
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
            ex.getBindingResult().getAllErrors().get(0).getDefaultMessage());
        return pd;
    }
}
```

> 🔑 **RFC 9457** is the IETF standard for HTTP API error responses. It defines the `type`, `title`, `status`, `detail`, `instance` fields. Consistent error format means API clients can handle errors generically.

---

## 11. Layer 9 — Service Layer (`recon-api`)

### 📄 `ReconResultServiceImpl.java`

```java
// recon-api/.../service/ReconResultServiceImpl.java
@Service
@RequiredArgsConstructor
public class ReconResultServiceImpl implements ReconResultService {

    // @Transactional(readOnly = true) tells Hibernate:
    //   - Don't track dirty state → faster
    //   - Use a read-replica if one is configured
    @Transactional(readOnly = true)
    public Page<ReconResultDto> getResults(LocalDate reportDate, String entityId,
                                           MatchStatus status, Severity severity,
                                           int page, int size) {
        var pageable = PageRequest.of(page, size);
        var entities = reconResultRepository
            .findByReportDateAndEntityIdAndMatchStatusAndSeverity(
                reportDate, entityId, status, severity, pageable);

        // Convert entity → DTO (never expose JPA entities directly to the API)
        return new PageImpl<>(
            entities.stream().map(mapper::toDto).toList(),
            pageable,
            entities.getTotalElements()
        );
    }

    @Transactional   // writable — needs flush on commit
    public ReconResultDto resolve(String reconId, String resolutionNote) {
        ReconResult entity = reconResultRepository.findByReconId(reconId)
            .orElseThrow(() -> new IllegalArgumentException("Unknown reconId: " + reconId));
        
        entity.setResolved(true);
        entity.setBreakReason(resolutionNote);
        entity.setUpdatedAt(OffsetDateTime.now());
        
        return mapper.toDto(reconResultRepository.save(entity));
    }
}
```

> 🔑 **Why separate DTOs from entities?** The `ReconResult` entity has a Hibernate proxy and lazy-loaded collections. Serializing it directly to JSON can trigger N+1 queries or `LazyInitializationException`. `ReconResultDto` is a plain record — safe to serialize.

---

## 12. Layer 10 — Notifications (`recon-notification`)

### 📄 `NotificationServiceImpl.java` — conditional on mail being configured

```java
@Service
@ConditionalOnBean(JavaMailSender.class)  // only active when spring.mail.host is set
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    @Async   // runs in a separate thread — doesn't block the caller
    public void notifyBreak(ReconResult result) {
        if (result.getSeverity() == Severity.CRITICAL) {
            sendEmail(result);    // email for CRITICAL only
        }
        if (result.getSeverity() == Severity.HIGH || result.getSeverity() == Severity.CRITICAL) {
            sendSlack(result);    // Slack for HIGH and CRITICAL
        }
    }
}
```

### 📄 `NoOpNotificationService.java` — dev/test fallback

```java
@Service
@ConditionalOnMissingBean(NotificationServiceImpl.class)  // active only when mail NOT configured
@Slf4j
public class NoOpNotificationService implements NotificationService {

    @Override
    public void notifyBreak(ReconResult result) {
        log.debug("NoOp notification: break {} severity={}", result.getReconId(), result.getSeverity());
        // Silently drops all notifications — safe for local development
    }
}
```

> 🔑 **`@ConditionalOnBean` / `@ConditionalOnMissingBean`** — Spring's condition system. In production with `spring.mail.host` set: `NotificationServiceImpl` wins. Locally without mail config: `NoOpNotificationService` wins. The rest of the application is completely unaware of which implementation is active.

---

## 13. Configuration Deep Dive

### 📄 `application.yml` — annotated

```yaml
spring:
  autoconfigure:
    exclude:
      # We define our own KafkaTemplate<String, FileArrivedEvent>
      # Excluding this prevents Spring Boot creating a competing KafkaTemplate<Object,Object>
      - org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration

  datasource:
    url: jdbc:postgresql://localhost:5432/recondb
    # ${DB_PASS:recon_pass} = read from env var DB_PASS, fallback to "recon_pass"
    password: ${DB_PASS:recon_pass}
    hikari:
      maximum-pool-size: 20    # 20 concurrent DB connections
      connection-timeout: 30000

  jpa:
    hibernate:
      ddl-auto: validate     # NEVER use create/update in production — use Flyway
    properties:
      hibernate:
        jdbc:
          batch_size: 500    # insert 500 rows at a time instead of 1-by-1

  batch:
    jdbc:
      initialize-schema: always  # Spring Batch creates its own metadata tables

recon:
  landing-zone:
    mode: local              # local = watch filesystem, s3 = poll S3
    path: ${LANDING_ZONE_PATH:/data/landing}
    poll-interval-ms: 5000   # check every 5 seconds (only for S3 mode)

  processing:
    tolerance:
      default: 0.00
      low-threshold: 1000    # $1K = LOW severity
      medium-threshold: 10000
      high-threshold: 100000 # ≥$100K = CRITICAL
```

### 📄 `JpaConfig.java` — cross-module entity scanning

```java
// recon-api/.../config/JpaConfig.java
@Configuration
public class JpaConfig {

    @Bean @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        
        // Scan ALL packages under com.recon — not just com.recon.api
        // Without this, Hibernate only finds entities in the main app's package
        factory.setPackagesToScan("com.recon");
        
        factory.setPersistenceUnitName("default");
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        return factory;
    }
}
```

> 🔑 **The multi-module JPA problem:** Spring Boot's `@SpringBootApplication` scans `com.recon.api.*` by default. Entities live in `com.recon.storage.entity.*`. `JpaConfig.setPackagesToScan("com.recon")` fixes this.

---

## 14. Test Code Walkthrough

### 📄 `FlatFileParserServiceTest.java` — unit test

```java
// recon-ingestion/src/test/java/.../FlatFileParserServiceTest.java
class FlatFileParserServiceTest {

    private final FlatFileParserService service = new FlatFileParserService();

    @Test
    void parsePipeDelimited_validFile_returnsAllRecords() throws Exception {
        Path file = Path.of(getClass().getResource("/sample/SRCA_RECON_20260428_093000_001.dat").toURI());
        ParseResult result = service.parse(file, SourceSystem.CORE_BANKING);

        assertThat(result.successRecords()).hasSize(5);
        assertThat(result.errorRecords()).isEmpty();
        assertThat(result.controlTotal()).isEqualByComparingTo("135550000.00");
    }

    @Test
    void controlTotalMismatch_throwsException() throws Exception {
        Path file = Path.of(getClass().getResource("/sample/SRCA_RECON_BAD_TOTAL.dat").toURI());

        // The test expects this specific exception type
        assertThatThrownBy(() -> service.parse(file, SourceSystem.CORE_BANKING))
            .isInstanceOf(ControlTotalMismatchException.class);
    }
}
```

### 📄 `ValidationItemProcessorTest.java` — unit test

```java
class ValidationItemProcessorTest {

    // No Spring context — pure unit test, runs in milliseconds
    private final ValidationItemProcessor processor =
        new ValidationItemProcessor(Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void validRecordPasses() throws Exception {
        ReconStaging row = baseRow();     // helper creates a valid staging row
        ValidatedRecord result = processor.process(row);

        assertThat(result.rconCode()).isEqualTo("RCON0010");
        assertThat(result.balance()).isEqualByComparingTo("5000000.00");
    }

    @Test
    void invalidRconCodeFails() {
        ReconStaging row = baseRow();
        row.setRconCode("INVALID");

        // No Spring, no Kafka, no DB — pure business logic validation test
        assertThatThrownBy(() -> processor.process(row))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("RCON code");
    }
}
```

### 📄 `ReconMatchingProcessorTest.java` — unit test with Mockito

```java
@ExtendWith(MockitoExtension.class)
class ReconMatchingProcessorTest {

    @Mock
    RconToleranceConfigRepository toleranceRepo;   // mock — no DB needed

    @InjectMocks
    ReconMatchingProcessor processor;

    @Test
    void bothSidesMatch_producesMatchedResult() {
        // Configure the mock to return zero tolerance
        when(toleranceRepo.findByRconCode("RCON0010")).thenReturn(Optional.empty());

        List<ValidatedRecord> records = List.of(
            record(SourceSystem.CORE_BANKING, "5000000.00"),
            record(SourceSystem.LOANS_SYS,    "5000000.00")   // same amount
        );
        List<ReconResult> results = processor.processGroups(records);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getMatchStatus()).isEqualTo(MatchStatus.MATCHED);
    }

    @Test
    void missingCoreBankingProducesPartial() {
        when(toleranceRepo.findByRconCode(any())).thenReturn(Optional.empty());

        List<ValidatedRecord> records = List.of(
            record(SourceSystem.LOANS_SYS, "5000000.00")   // only one side
        );
        List<ReconResult> results = processor.processGroups(records);

        assertThat(results.get(0).getMatchStatus()).isEqualTo(MatchStatus.PARTIAL);
    }
}
```

> 🔑 **`@Mock` + `@InjectMocks`** = Mockito creates a fake `RconToleranceConfigRepository` and injects it into the processor. No database, no Spring context, runs in ~50ms. This is how you test business logic in isolation.

### 📄 `ReconResultControllerTest.java` — web layer test

```java
@WebMvcTest(ReconResultController.class)  // only loads the web layer, not the whole app
class ReconResultControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean  ReconResultService service;  // mock the service

    @Test
    void getResults_validParams_returns200() throws Exception {
        when(service.getResults(any(), any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/recon/results")
                .param("reportDate", "2026-04-28")
                .param("entityId", "E001")
                .param("status", "MATCHED")
                .param("severity", "LOW"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void getResults_missingParam_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/recon/results"))  // no params
            .andExpect(status().isBadRequest());
    }
}
```

> 🔑 **`@WebMvcTest`** only starts the Spring MVC layer (controllers, filters, serialization). It's 10x faster than `@SpringBootTest` which starts everything. Use it to test HTTP contract: correct status codes, response shapes, parameter validation.

---

## 15. Full Request Trace — File to API Response

Follow one file through the entire system:

```
╔══════════════════════════════════════════════════════════════════════╗
║  FILE: SRCA_RECON_20260428_093000_001.dat dropped in data/landing/  ║
╚══════════════════════════════════════════════════════════════════════╝
               │
               ▼
┌─────────────────────────────────────────────────────────────────────┐
│  LocalFileWatcherService.handleFile()                               │
│  ├─ resolveSourceSystem("SRCA_...") → CORE_BANKING                 │
│  ├─ parseDate("SRCA_RECON_20260428...") → 2026-04-28               │
│  ├─ FileRegistryService.register() → INSERT recon_file_registry    │
│  └─ FileEventProducer.publishFileArrived() → Kafka                  │
└─────────────────────────────────────────────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────────────────────────────┐
│  Kafka topic: recon.file.arrived                                     │
│  Message: { fileId: "SRCA_20260428_001", sourceSystem: CORE_BANKING }│
└─────────────────────────────────────────────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────────────────────────────┐
│  FlatFileParserService.parse() — .dat → parsePipeDelimited()        │
│  ├─ Read HDR: count=5, total=135550000.00                           │
│  ├─ Parse 5 DTL lines (virtual thread per batch)                    │
│  │    DTL|SRCA000001|...|RCON0010|5000000.00|CR|USD                │
│  │    DTL|SRCA000002|...|RCON0071|1250000.00|CR|USD                │
│  │    ... 3 more records                                            │
│  ├─ Validate TRL: actual=135550000.00 == expected ✓                │
│  └─ Return ParseResult: 5 success, 0 errors                         │
└─────────────────────────────────────────────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────────────────────────────┐
│  INSERT 5 rows into recon_staging (processed=false)                  │
└─────────────────────────────────────────────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────────────────────────────┐
│  ValidationItemProcessor.process() — for each staging row           │
│  ├─ RCON0010 matches pattern ^RCON\d{4}$ ✓                         │
│  ├─ balance 5000000.00 ≥ 0 ✓                                       │
│  ├─ currency USD is valid ISO 4217 ✓                               │
│  ├─ SRCA000001 not in Bloom Filter → add it ✓                     │
│  └─ Return ValidatedRecord                                          │
└─────────────────────────────────────────────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────────────────────────────┐
│  ReconMatchingProcessor.processGroups()                              │
│  ├─ Group by "2026-04-28|E001|RCON0010"                            │
│  ├─ srcA = CORE_BANKING(5000000.00)                                │
│  ├─ srcB = null (no LOANS_SYS file yet for this date)              │
│  ├─ status = UNMATCHED (one side only)                              │
│  └─ severity = LOW (|5000000| < N/A, single-sided → LOW default)   │
└─────────────────────────────────────────────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────────────────────────────┐
│  INSERT 5 rows into recon_results (partition: recon_results_2026_04) │
│  recon_id="SRCA_20260428_001-SRCA000001", match_status=UNMATCHED    │
└─────────────────────────────────────────────────────────────────────┘
               │
               ▼
╔══════════════════════════════════════════════════════════════════════╗
║  GET /api/v1/recon/results?reportDate=2026-04-28                    ║
║     &entityId=E001&status=UNMATCHED&severity=LOW                    ║
╚══════════════════════════════════════════════════════════════════════╝
               │
               ▼
┌─────────────────────────────────────────────────────────────────────┐
│  ReconResultController.results()                                     │
│  └─ ReconResultServiceImpl.getResults()                             │
│       └─ reconResultRepository.findByReportDate...()               │
│            └─ SELECT * FROM recon_results_2026_04                   │
│                 WHERE report_date='2026-04-28'                       │
│                 AND entity_id='E001'                                 │
│                 AND match_status='UNMATCHED'                         │
│            └─ mapper.toDto(entity) → ReconResultDto                 │
│       └─ Return Page<ReconResultDto>                                 │
└─────────────────────────────────────────────────────────────────────┘
               │
               ▼
╔══════════════════════════════════════════════════════════════════════╗
║  HTTP 200 OK                                                         ║
║  { "content": [{ "reconId": "SRCA_20260428_001-SRCA000001",        ║
║                  "matchStatus": "UNMATCHED", ... }],                ║
║    "totalElements": 5 }                                              ║
╚══════════════════════════════════════════════════════════════════════╝
```

---

## Key Design Patterns Summary

| Pattern | Where used | Why |
|---|---|---|
| **Module separation** | 7 Maven modules | Each module has one responsibility; can be tested independently |
| **Java Records** | All DTOs in `recon-common` | Immutability prevents mutation bugs in the pipeline |
| **Conditional beans** | `NotificationServiceImpl`, `LocalFileWatcherService` | Feature flags via configuration — no `if` statements scattered in code |
| **Virtual threads** | File watcher, parser, matching engine | Handle thousands of concurrent I/O operations without thread pool exhaustion |
| **Bloom filter** | `ValidationItemProcessor` | O(1) duplicate detection for millions of records without DB round-trips |
| **DB partitioning** | `recon_results` | Query only one month's partition instead of scanning all history |
| **Partial index** | `idx_staging_processed` | Only index unprocessed rows — shrinks to near-zero after daily batch |
| **GENERATED column** | `recon_results.difference` | DB computes `balanceA - balanceB` — no risk of Java rounding error |
| **Convention over config** | File name prefix routing | `SRCA_` → CORE_BANKING without any database lookup |
| **Error collection** | File parser | Bad rows don't abort good rows — maximum data throughput |
| **`@WebMvcTest`** | Controller tests | Test HTTP layer without starting DB, Kafka, or file watcher |
| **`@Mock` + `@InjectMocks`** | Processor tests | Test matching logic without any infrastructure dependencies |

