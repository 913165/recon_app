# GitHub Copilot Prompt — RCON Reconciliation System (Spring Boot)

> Paste this entire document into GitHub Copilot Chat or use it as a workspace prompt.
> It describes a complete, production-grade reconciliation processing application.

---

## Project overview

Build a **Spring Boot reconciliation processing application** that ingests flat files
from three different source systems (Core Banking, Loans/Deposits, Trading/GL),
processes millions of records through a validation and matching pipeline, stores
results in a partitioned PostgreSQL database, and exposes REST APIs for dashboards
and break alerting.

---

## Technology stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java (LTS) | 25 (GA Sep 2025) |
| Framework | Spring Boot | 4.0.6 |
| Spring core | Spring Framework | 7.0.5 |
| Build tool | Maven | 3.9.x (Gradle 9 also supported) |
| Batch processing | Spring Batch | 6.x (ships with Spring Boot 4) |
| Messaging | Spring Kafka | 4.0.5 |
| Message broker | Apache Kafka | 4.2.0 |
| Database | PostgreSQL | 17.9 |
| ORM | Spring Data JPA + Hibernate | 7.x |
| DB migrations | Flyway | 11.x |
| Bulk load | Spring JDBC (COPY via PGConnection) | — |
| Validation | Jakarta Bean Validation + Hibernate Validator | 8.x |
| Null safety | JSpecify | 1.0 |
| REST API | Spring Web MVC | (via Spring Boot 4) |
| HTTP clients | Spring `@HttpExchange` interface | (via Spring Framework 7) |
| Scheduling | Spring Scheduler + Quartz | 2.5.x |
| Monitoring | Spring Actuator + Micrometer + OpenTelemetry | 1.16.x / 1.x |
| Tracing | OpenTelemetry (OTLP exporter) | 1.x |
| Native image | GraalVM Native Build Tools | 0.10.x |
| Security | Spring Security | 7.0.x |
| API docs | SpringDoc OpenAPI | 3.x |
| Testing | JUnit 5 + Mockito + Testcontainers | 5.x / 5.x / 1.21.x |
| Containerisation | Docker + Docker Compose | v3.9 spec |
| Config | Spring Cloud Config + `application.yml` profiles | 4.x |
| Lombok | Project Lombok | 1.18.x |

> **Java 25 key features used in this project:**
> - Virtual threads (Project Loom — GA since Java 21, enhanced in 25)
> - Structured concurrency (finalized in Java 25)
> - Sequenced collections
> - Pattern matching in `switch` + primitive types in patterns (JEP 507)
> - Scoped values (replaces ThreadLocal for virtual threads)
> - Flexible constructor bodies (JEP 513)
> - Module import declarations (JEP 512)

---

## Module structure

Generate a multi-module Maven project with the following structure:

```
recon-system/
├── pom.xml                          (parent POM — Spring Boot 4.0.6 parent)
├── recon-common/                    (shared DTOs, constants, utils)
│   └── src/main/java/com/recon/common/
│       ├── dto/                     (Java Records only — no classes)
│       ├── enums/
│       └── util/
├── recon-ingestion/                 (file parser + Kafka producer)
│   └── src/main/java/com/recon/ingestion/
│       ├── config/
│       ├── parser/
│       ├── kafka/
│       └── watcher/
├── recon-processing/                (Spring Batch 6 jobs — validator + recon engine)
│   └── src/main/java/com/recon/processing/
│       ├── config/
│       ├── job/
│       ├── step/
│       ├── processor/
│       └── writer/
├── recon-storage/                   (PostgreSQL 17 repositories + bulk loader)
│   └── src/main/java/com/recon/storage/
│       ├── entity/
│       ├── repository/
│       └── bulk/
├── recon-api/                       (REST API — results, breaks, dashboard)
│   └── src/main/java/com/recon/api/
│       ├── controller/
│       ├── service/
│       └── mapper/
└── recon-notification/              (break alerts — email, Slack, JIRA)
    └── src/main/java/com/recon/notification/
        ├── service/
        └── template/
```

---

## Parent POM

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.6</version>
</parent>

<properties>
    <java.version>25</java.version>
    <maven.compiler.release>25</maven.compiler.release>
    <spring-kafka.version>4.0.5</spring-kafka.version>
    <flyway.version>11.3.0</flyway.version>
    <testcontainers.version>1.21.0</testcontainers.version>
    <springdoc.version>3.0.0</springdoc.version>
    <jspecify.version>1.0.0</jspecify.version>
</properties>
```

---

## Database schema

Generate Flyway migration scripts under `recon-storage/src/main/resources/db/migration/`.

### V1__create_staging_table.sql

```sql
CREATE TABLE recon_staging (
    id                BIGSERIAL PRIMARY KEY,
    file_id           VARCHAR(50)    NOT NULL,
    source_system     VARCHAR(20)    NOT NULL,  -- CORE_BANKING | LOANS_SYS | TRADING_GL
    record_id         VARCHAR(30)    NOT NULL,
    report_date       DATE           NOT NULL,
    entity_id         VARCHAR(10)    NOT NULL,
    entity_name       VARCHAR(100),
    account_code      VARCHAR(15)    NOT NULL,
    account_desc      VARCHAR(100),
    rcon_code         VARCHAR(10)    NOT NULL,
    balance           NUMERIC(20,2)  NOT NULL,
    dr_cr_ind         CHAR(2)        NOT NULL,
    currency          CHAR(3)        NOT NULL DEFAULT 'USD',
    as_of_time        TIME,
    source_ref        VARCHAR(50),
    comments          VARCHAR(255),
    loaded_at         TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    processed         BOOLEAN        NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_staging_report_date   ON recon_staging(report_date);
CREATE INDEX idx_staging_source        ON recon_staging(source_system);
CREATE INDEX idx_staging_processed     ON recon_staging(processed) WHERE processed = FALSE;
```

### V2__create_recon_results_table.sql

```sql
CREATE TABLE recon_results (
    id                BIGSERIAL,
    recon_id          VARCHAR(40)    NOT NULL,
    report_date       DATE           NOT NULL,
    entity_id         VARCHAR(10)    NOT NULL,
    rcon_code         VARCHAR(10)    NOT NULL,
    source_system_a   VARCHAR(20)    NOT NULL,
    balance_a         NUMERIC(20,2),
    source_system_b   VARCHAR(20)    NOT NULL,
    balance_b         NUMERIC(20,2),
    difference        NUMERIC(20,2)  GENERATED ALWAYS AS (balance_a - balance_b) STORED,
    tolerance         NUMERIC(10,2)  NOT NULL DEFAULT 0.00,
    match_status      VARCHAR(15)    NOT NULL,  -- MATCHED | BREAK | PARTIAL | UNMATCHED
    severity          VARCHAR(10),              -- LOW | MEDIUM | HIGH | CRITICAL
    currency          CHAR(3)        NOT NULL DEFAULT 'USD',
    analyst_assigned  VARCHAR(50),
    break_reason      VARCHAR(255),
    resolved          BOOLEAN        NOT NULL DEFAULT FALSE,
    resolved_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id, report_date)
) PARTITION BY RANGE (report_date);

-- Monthly partitions — generate for current + next year
CREATE TABLE recon_results_2026_01 PARTITION OF recon_results
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE recon_results_2026_02 PARTITION OF recon_results
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
-- ... repeat for each month

-- BRIN index — optimal for date range scans on append-only partitions (PostgreSQL 17)
CREATE INDEX idx_recon_results_date_brin ON recon_results USING BRIN (report_date);
CREATE INDEX idx_recon_results_status    ON recon_results(match_status);
CREATE INDEX idx_recon_results_entity    ON recon_results(entity_id);
CREATE INDEX idx_recon_results_severity  ON recon_results(severity) WHERE resolved = FALSE;
```

### V3__create_audit_errors_table.sql

```sql
CREATE TABLE recon_audit_errors (
    id                BIGSERIAL PRIMARY KEY,
    file_id           VARCHAR(50)    NOT NULL,
    source_system     VARCHAR(20)    NOT NULL,
    record_id         VARCHAR(30),
    report_date       DATE,
    error_type        VARCHAR(30)    NOT NULL,  -- PARSE_ERROR | VALIDATION_ERROR | DUPLICATE | SYSTEM_ERROR
    error_message     TEXT           NOT NULL,
    raw_record        TEXT,
    retry_count       INT            NOT NULL DEFAULT 0,
    reprocess_flag    BOOLEAN        NOT NULL DEFAULT FALSE,
    reprocessed_at    TIMESTAMPTZ,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_reprocess ON recon_audit_errors(reprocess_flag) WHERE reprocess_flag = TRUE;
CREATE INDEX idx_audit_file_id   ON recon_audit_errors(file_id);
```

### V4__create_file_registry_table.sql

```sql
CREATE TABLE recon_file_registry (
    id                BIGSERIAL PRIMARY KEY,
    file_id           VARCHAR(50)    NOT NULL UNIQUE,
    file_name         VARCHAR(255)   NOT NULL,
    source_system     VARCHAR(20)    NOT NULL,
    file_path         VARCHAR(500)   NOT NULL,
    report_date       DATE           NOT NULL,
    total_records     BIGINT,
    loaded_records    BIGINT,
    failed_records    BIGINT,
    control_amount    NUMERIC(25,2),
    file_status       VARCHAR(20)    NOT NULL DEFAULT 'RECEIVED',
    -- RECEIVED | PARSING | PARSED | VALIDATING | VALIDATED | PROCESSING | COMPLETED | FAILED
    received_at       TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    completed_at      TIMESTAMPTZ,
    kafka_offset      BIGINT,
    kafka_partition   INT
);
```

---

## Core domain model

Generate these JPA entities in `recon-storage/src/main/java/com/recon/storage/entity/`.
Use JSpecify `@Nullable` / `@NonNull` annotations on all fields.

### ReconStaging.java

```java
@Entity
@Table(name = "recon_staging")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconStaging {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private @NonNull Long id;

    @Column(name = "file_id", nullable = false, length = 50)
    private @NonNull String fileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_system", nullable = false)
    private @NonNull SourceSystem sourceSystem;

    @Column(name = "record_id", nullable = false, length = 30)
    private @NonNull String recordId;

    @Column(name = "report_date", nullable = false)
    private @NonNull LocalDate reportDate;

    @Column(name = "entity_id", nullable = false, length = 10)
    private @NonNull String entityId;

    @Column(name = "rcon_code", nullable = false, length = 10)
    private @NonNull String rconCode;

    @Column(name = "balance", nullable = false, precision = 20, scale = 2)
    private @NonNull BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(name = "dr_cr_ind", nullable = false, length = 2)
    private @NonNull DrCrIndicator drCrInd;

    @Column(name = "currency", length = 3)
    private @NonNull String currency = "USD";

    @Column(name = "processed")
    private @NonNull Boolean processed = false;

    @Column(name = "loaded_at")
    private @Nullable OffsetDateTime loadedAt;
}
```

### ReconResult.java

```java
@Entity
@Table(name = "recon_results")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private @NonNull Long id;

    @Column(name = "recon_id", nullable = false, length = 40)
    private @NonNull String reconId;

    @Column(name = "report_date", nullable = false)
    private @NonNull LocalDate reportDate;

    @Column(name = "entity_id", nullable = false)
    private @NonNull String entityId;

    @Column(name = "rcon_code", nullable = false)
    private @NonNull String rconCode;

    @Column(name = "balance_a", precision = 20, scale = 2)
    private @Nullable BigDecimal balanceA;

    @Column(name = "balance_b", precision = 20, scale = 2)
    private @Nullable BigDecimal balanceB;

    @Column(name = "tolerance", precision = 10, scale = 2)
    private @NonNull BigDecimal tolerance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_status", nullable = false)
    private @NonNull MatchStatus matchStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity")
    private @Nullable Severity severity;

    @Column(name = "break_reason")
    private @Nullable String breakReason;

    @Column(name = "resolved")
    private @NonNull Boolean resolved = false;

    @Column(name = "created_at")
    private @NonNull OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private @NonNull OffsetDateTime updatedAt;
}
```

### DTOs — use Java Records (Java 25)

```java
// All DTOs must be Java Records, never classes
public record ReconResultDto(
    @NonNull String reconId,
    @NonNull LocalDate reportDate,
    @NonNull String entityId,
    @NonNull String rconCode,
    @Nullable BigDecimal balanceA,
    @Nullable BigDecimal balanceB,
    @Nullable BigDecimal difference,
    @NonNull MatchStatus matchStatus,
    @Nullable Severity severity,
    @NonNull Boolean resolved
) {}

public record ReconSummaryDto(
    @NonNull LocalDate reportDate,
    long totalRecords,
    long matchedCount,
    long breakCount,
    long unmatchedCount,
    @NonNull BigDecimal matchRate,
    @NonNull BigDecimal totalBreakAmount
) {}

public record FileArrivedEvent(
    @NonNull String fileId,
    @NonNull String fileName,
    @NonNull SourceSystem sourceSystem,
    @NonNull String filePath,
    @NonNull LocalDate reportDate,
    @NonNull OffsetDateTime arrivedAt
) {}
```

### Enums

```java
// recon-common/src/main/java/com/recon/common/enums/
public enum SourceSystem  { CORE_BANKING, LOANS_SYS, TRADING_GL }
public enum MatchStatus   { MATCHED, BREAK, PARTIAL, UNMATCHED }
public enum Severity      { LOW, MEDIUM, HIGH, CRITICAL }
public enum DrCrIndicator { DR, CR }
public enum FileStatus    { RECEIVED, PARSING, PARSED, VALIDATING, VALIDATED, PROCESSING, COMPLETED, FAILED }
```

---

## File ingestion layer

### File watcher (recon-ingestion)

Generate `FileWatcherService.java` that:
- Uses `WatchService` (Java NIO) to monitor a configurable landing directory
- Detects new `.dat`, `.csv`, and `.txt` files
- Determines source system from filename prefix (`SRCA_`, `SRCB_`, `SRCC_`)
- Publishes a `FileArrivedEvent` to Kafka topic `recon.file.arrived`
- Registers the file in `recon_file_registry` with status `RECEIVED`
- Uses **virtual threads** (`Thread.ofVirtual()`) for non-blocking file watching
- Supports S3 bucket polling as an alternative via `@ConditionalOnProperty`

### Kafka producer

Generate `FileEventProducer.java`:

```java
@Service
@RequiredArgsConstructor
public class FileEventProducer {

    private final KafkaTemplate<String, FileArrivedEvent> kafkaTemplate;

    @Value("${recon.kafka.topics.file-arrived}")
    private String topic;

    public void publishFileArrived(@NonNull FileArrivedEvent event) {
        kafkaTemplate.send(topic, event.fileId(), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish event for file {}", event.fileId(), ex);
                }
            });
    }
}
```

### File parser

Generate `FlatFileParserService.java` that handles three formats:
- **Pipe-delimited** (`.dat`) with HDR/DTL/TRL record types
- **CSV** with standard header row
- **Fixed-width** for legacy mainframe files (column positions configurable via yml)

Parser must:
- Auto-detect format from file extension and first bytes
- Map columns to `ReconStagingDto` using configurable schema mappings per source
- Handle EBCDIC encoding for legacy files
- Validate HDR control total against sum of DTL records
- Use **structured concurrency** (`StructuredTaskScope`) for parsing large files in parallel
- Return `ParseResult` record containing parsed records, failed records, and summary stats

---

## Spring Batch 6 processing pipeline

Generate a Spring Batch job `reconProcessingJob` in `recon-processing/` with the following steps.
Spring Batch 6 ships with Spring Boot 4 — use the updated `JobRepository` and `StepBuilder` APIs.

### Step 1 — StagingToValidationStep

```java
@Bean
public Step stagingToValidationStep(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        StagingItemReader stagingItemReader,
        ValidationItemProcessor validationProcessor,
        ValidatedItemWriter validatedItemWriter) {

    return new StepBuilder("stagingToValidationStep", jobRepository)
        .<ReconStaging, ValidatedRecord>chunk(1000, transactionManager)
        .reader(stagingItemReader)
        .processor(validationProcessor)
        .writer(validatedItemWriter)
        .faultTolerant()
        .skipLimit(100)
        .skip(ValidationException.class)
        .retryLimit(3)
        .retry(TransientDataAccessException.class)
        .listener(new ValidationStepListener())
        .build();
}
```

### Step 2 — ReconMatchingStep

```java
@Bean
public Step reconMatchingStep(
        JobRepository jobRepository,
        PlatformTransactionManager transactionManager,
        ValidatedRecordReader validatedReader,
        ReconMatchingProcessor matchingProcessor,
        ReconResultWriter resultWriter) {

    return new StepBuilder("reconMatchingStep", jobRepository)
        .<ValidatedRecord, ReconResult>chunk(500, transactionManager)
        .reader(validatedReader)
        .processor(matchingProcessor)
        .writer(resultWriter)
        .build();
}
```

### Validation processor

Generate `ValidationItemProcessor.java` that validates:
- Non-null required fields using JSpecify `@NonNull` constraints
- RCON code format (`RCON` + 4 digits) via `@ValidRconCode` custom annotation
- Balance is non-negative
- Currency is valid ISO 4217 code
- DrCrIndicator is DR or CR
- Duplicate detection using a Bloom filter (Guava) for performance
- Uses Hibernate Validator 8 with Jakarta Bean Validation 3.1

### Recon matching processor

Generate `ReconMatchingProcessor.java` that:
- Groups records by `(reportDate, entityId, rconCode)`
- Matches Source A (CORE_BANKING) against Source B (LOANS_SYS) and Source C (TRADING_GL)
- Calculates `difference = balanceA - balanceB`
- Applies configurable tolerance thresholds per RCON code from DB table `rcon_tolerance_config`
- Assigns `MatchStatus`:
    - `MATCHED` if `abs(difference) <= tolerance`
    - `PARTIAL` if one source has data and the other is missing
    - `BREAK` if `abs(difference) > tolerance`
    - `UNMATCHED` if no corresponding record exists in either source
- Assigns `Severity` based on `abs(difference)`:
    - `LOW` < 1,000
    - `MEDIUM` 1,000–10,000
    - `HIGH` 10,000–100,000
    - `CRITICAL` > 100,000
- Use **virtual threads** for parallel group processing via `Executors.newVirtualThreadPerTaskExecutor()`

---

## Bulk PostgreSQL 17 loader

Generate `PostgresBulkLoader.java` in `recon-storage/` using `COPY` for high-throughput inserts:

```java
@Service
@RequiredArgsConstructor
public class PostgresBulkLoader {

    private final DataSource dataSource;

    public int bulkInsertStaging(@NonNull List<ReconStagingDto> records) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            CopyManager copyManager = conn.unwrap(PGConnection.class).getCopyAPI();

            StringBuilder sb = new StringBuilder();
            for (ReconStagingDto r : records) {
                sb.append(toCsvLine(r)).append("\n");
            }

            String sql = """
                COPY recon_staging (file_id, source_system, record_id, report_date,
                entity_id, rcon_code, balance, dr_cr_ind, currency)
                FROM STDIN WITH CSV
                """;

            try (InputStream is = new ByteArrayInputStream(
                    sb.toString().getBytes(StandardCharsets.UTF_8))) {
                return (int) copyManager.copyIn(sql, is);
            }
        }
    }
}
```

Note: Use Java 25 text blocks (`"""..."""`) for all multi-line SQL strings.

---

## Virtual thread configuration (Java 25 + Spring Boot 4)

Spring Boot 4 auto-configures virtual threads when Java 21+ is detected.
Ensure the following in `application.yml` to enable them explicitly:

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

This makes all `@Async` tasks, Tomcat request handling, and `@Scheduled` tasks
run on virtual threads automatically — no manual `Executors` configuration needed
for web request handling.

---

## REST API layer

Generate REST controllers in `recon-api/` using Spring Framework 7's `@HttpExchange`
for outbound HTTP clients, and standard `@RestController` for inbound APIs.

### ReconResultController

```
GET  /api/v1/recon/results
     ?reportDate=2026-04-28
     &entityId=E001
     &status=BREAK
     &severity=HIGH
     &page=0&size=50
     → Page<ReconResultDto>

GET  /api/v1/recon/results/{reconId}
     → ReconResultDto

PATCH /api/v1/recon/results/{reconId}/resolve
      Body: { "resolutionNote": "Timing difference confirmed" }
      → ReconResultDto

GET  /api/v1/recon/summary
     ?reportDate=2026-04-28
     → ReconSummaryDto

GET  /api/v1/recon/results/{reconId}/history
     → List<ReconAuditDto>
```

### FileRegistryController

```
GET  /api/v1/files
     ?reportDate=2026-04-28&sourceSystem=CORE_BANKING
     → List<FileRegistryDto>

GET  /api/v1/files/{fileId}/status
     → FileStatusDto

POST /api/v1/files/{fileId}/reprocess
     → triggers reprocessing of failed file
```

### API versioning (Spring Framework 7 built-in)

Spring Framework 7 adds first-class API versioning. Configure it as follows:

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(ApiVersioningConfigurer configurer) {
        configurer
            .useRequestHeader("API-Version")
            .useRequestParameter("version")
            .defaultVersion(ApiVersion.of("1"));
    }
}

// Use @ApiVersion on controllers
@RestController
@ApiVersion("1")
@RequestMapping("/api/recon/results")
public class ReconResultV1Controller { ... }
```

---

## OpenTelemetry observability (Spring Boot 4)

Spring Boot 4 ships an `OpenTelemetry` starter. Add to `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-otel</artifactId>
</dependency>
```

Configure in `application.yml`:

```yaml
management:
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/traces}
    metrics:
      export:
        endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://localhost:4318/v1/metrics}
  tracing:
    sampling:
      probability: 1.0
```

---

## Notification service

Generate `NotificationService.java` in `recon-notification/` that:
- Sends email alerts via Spring Mail for CRITICAL breaks
- Sends Slack webhook messages for HIGH and CRITICAL breaks
- Creates JIRA tickets via `@HttpExchange` client (Spring Framework 7) for unresolved breaks older than 24h
- Uses `@Async` with virtual thread executor — no blocking
- Templates are Thymeleaf templates in `resources/templates/`

---

## Kafka configuration (Spring Kafka 4 + Kafka 4.2)

Generate `KafkaConfig.java`.
Note: Apache Kafka 4.x removes ZooKeeper — use KRaft mode only.

```java
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, FileArrivedEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "recon-processing-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        return new DefaultKafkaConsumerFactory<>(props,
            new StringDeserializer(),
            new JsonDeserializer<>(FileArrivedEvent.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, FileArrivedEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, FileArrivedEvent> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
}
```

Kafka topics (KRaft mode — no ZooKeeper):
- `recon.file.arrived` (partitions: 3, replication: 1)
- `recon.processing.completed` (partitions: 3, replication: 1)
- `recon.break.alerts` (partitions: 1, replication: 1)

---

## Application configuration

Generate `application.yml`:

```yaml
spring:
  application:
    name: recon-system
  threads:
    virtual:
      enabled: true          # Java 25 virtual threads — auto-enables for Tomcat + @Async
  datasource:
    url: jdbc:postgresql://localhost:5432/recondb
    username: ${DB_USER:recon_user}
    password: ${DB_PASS:recon_pass}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        jdbc:
          batch_size: 500
          order_inserts: true
          order_updates: true
  kafka:
    bootstrap-servers: ${KAFKA_SERVERS:localhost:9092}
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
  batch:
    jdbc:
      initialize-schema: always
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

recon:
  landing-zone:
    path: ${LANDING_ZONE_PATH:/data/landing}
    poll-interval-ms: 5000
  kafka:
    topics:
      file-arrived: recon.file.arrived
      processing-completed: recon.processing.completed
      break-alerts: recon.break.alerts
  processing:
    chunk-size: 1000
    tolerance:
      default: 0.00
      low-threshold: 1000
      medium-threshold: 10000
      high-threshold: 100000
  notification:
    slack-webhook-url: ${SLACK_WEBHOOK_URL:}
    jira-url: ${JIRA_URL:}
    jira-project-key: RECON
    email:
      to: ${ALERT_EMAIL:ops@company.com}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  otlp:
    tracing:
      endpoint: ${OTEL_ENDPOINT:http://localhost:4318/v1/traces}
  tracing:
    sampling:
      probability: 1.0
```

---

## Docker Compose (Kafka KRaft — no ZooKeeper)

Generate `docker-compose.yml` at project root.
Apache Kafka 4.x uses KRaft mode — ZooKeeper is removed.

```yaml
services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: recondb
      POSTGRES_USER: recon_user
      POSTGRES_PASSWORD: recon_pass
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

  kafka:
    image: apache/kafka:4.2.0        # KRaft mode — no ZooKeeper needed
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1

  otel-collector:
    image: otel/opentelemetry-collector-contrib:latest
    ports:
      - "4318:4318"    # OTLP HTTP
      - "4317:4317"    # OTLP gRPC

  recon-app:
    build: .
    depends_on: [postgres, kafka]
    ports:
      - "8080:8080"
    environment:
      DB_USER: recon_user
      DB_PASS: recon_pass
      KAFKA_SERVERS: kafka:9092
      LANDING_ZONE_PATH: /data/landing
      OTEL_ENDPOINT: http://otel-collector:4318/v1/traces
    volumes:
      - ./data/landing:/data/landing

volumes:
  pgdata:
```

---

## Unit and integration tests

Generate the following test classes using JUnit 5 + Mockito 5 + Testcontainers 1.21.

### FlatFileParserServiceTest.java
- Test parsing of pipe-delimited file with HDR/DTL/TRL
- Test parsing of CSV file
- Test fixed-width legacy file parsing
- Test control total mismatch throws `ControlTotalMismatchException`
- Test malformed record is captured in error list

### ValidationItemProcessorTest.java
- Test valid record passes through unchanged
- Test null rconCode throws `ValidationException`
- Test invalid RCON code format fails `@ValidRconCode` constraint
- Test duplicate recordId is flagged via Bloom filter
- Test negative balance fails validation

### ReconMatchingProcessorTest.java
- Test two matching records produce `MATCHED` status
- Test difference within tolerance produces `MATCHED` status
- Test difference exceeding tolerance produces `BREAK` with correct `Severity`
- Test missing counterpart produces `UNMATCHED` status
- Test one source missing data produces `PARTIAL` status

### ReconResultControllerTest.java (MockMvc)
- Test GET `/api/v1/recon/results` returns paginated results
- Test GET `/api/v1/recon/summary` returns correct aggregate counts
- Test PATCH `/api/v1/recon/results/{id}/resolve` updates resolved flag
- Test `API-Version` header routing (Spring Framework 7 versioning)

### Integration test (Testcontainers 1.21)

Generate `ReconPipelineIntegrationTest.java`:

```java
@SpringBootTest
@Testcontainers
class ReconPipelineIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static KafkaContainer kafka =
        new KafkaContainer(DockerImageName.parse("apache/kafka:4.2.0"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    // End-to-end test: drop sample file → trigger watcher → assert recon_results populated
}
```

---

## Custom annotations

Generate these custom validation annotations in `recon-common/`:

```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RconCodeValidator.class)
public @interface ValidRconCode {
    String message() default "Invalid RCON code. Expected RCON followed by 4 digits.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

---

## Monitoring and metrics

Generate `ReconMetricsService.java` using Micrometer 1.16 with OpenTelemetry backend:
- `recon.files.received` — counter tagged by source system
- `recon.records.processed` — counter tagged by source + status
- `recon.breaks.total` — gauge: current unresolved breaks
- `recon.match.rate` — gauge: percentage matched today
- `recon.processing.duration` — timer: batch job execution time
- `recon.db.bulk.load.duration` — timer: COPY operation duration

Expose at `/actuator/prometheus` for Grafana scraping AND push via OTLP to collector.

---

## Error handling

Generate `GlobalExceptionHandler.java` using `@RestControllerAdvice`:

- `ValidationException` → 400 Bad Request
- `FileNotFoundException` → 404 Not Found
- `ControlTotalMismatchException` → 422 Unprocessable Entity
- `DuplicateFileException` → 409 Conflict
- `DataAccessException` → 503 Service Unavailable (with `Retry-After` header)

Use `ProblemDetail` (RFC 9457 — built into Spring Framework 6+) for all error responses:

```java
@ExceptionHandler(ValidationException.class)
ProblemDetail handleValidation(ValidationException ex) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    pd.setTitle("Validation Failed");
    pd.setProperty("timestamp", OffsetDateTime.now());
    return pd;
}
```

---

## GraalVM native image support

Spring Boot 4 has first-class GraalVM native image support.
Add the native build tools plugin to the parent POM:

```xml
<plugin>
    <groupId>org.graalvm.buildtools</groupId>
    <artifactId>native-maven-plugin</artifactId>
</plugin>
```

Register all reflection hints for JPA entities and Kafka serializers in
`ReconRuntimeHints.java` implementing `RuntimeHintsRegistrar`.

Build native image with:
```bash
mvn -Pnative native:compile
```

---

## Code generation instructions for Copilot

When generating this application, follow these conventions:

1. Use Java 25 — target `--release 25` in the compiler plugin
2. Use constructor injection everywhere — never `@Autowired` on fields
3. All service classes must have interfaces
4. Use `@Transactional(readOnly = true)` on read-only service methods
5. Use `@Transactional` only on write operations, not at class level
6. All DTOs must be Java Records — never mutable classes
7. Use JSpecify `@NonNull` / `@Nullable` on every field and method parameter
8. Use `Optional<>` return types on repository methods — never return null
9. All controller methods must have `@Operation` SpringDoc OpenAPI 3 annotations
10. Log at DEBUG for processing steps, INFO for file events, WARN for validation failures, ERROR for system failures
11. Use SLF4J with `@Slf4j` (Lombok) — never `System.out.println`
12. All monetary values must use `BigDecimal` — never `double` or `float`
13. All date/time fields must use `java.time` — never `java.util.Date` or `java.sql.Timestamp`
14. Use text blocks (`"""..."""`) for all multi-line SQL strings
15. Use virtual threads for all async and batch processing — `spring.threads.virtual.enabled=true`
16. Use structured concurrency (`StructuredTaskScope`) for parallel parsing tasks
17. Use `ProblemDetail` (RFC 9457) for all error responses — never custom error envelopes
18. Batch chunk size, topic names, thresholds must be externalised to `application.yml`
19. Every public method in service layer must have a corresponding unit test
20. Integration tests must use Testcontainers 1.21 with `postgres:17-alpine` and `apache/kafka:4.2.0`
21. Kafka is KRaft mode — never configure ZooKeeper
22. API versioning must use Spring Framework 7's built-in `@ApiVersion` — never URL path versioning
23. All observability via OpenTelemetry OTLP — Micrometer 1.16 with OTel bridge

---

## Sample flat file for testing

Place this file at `src/test/resources/sample/SRCA_RECON_20260428_093000_001.dat`:

```
HDR|SRCA_20260428_001|CORE_BANKING|20260428|E001|5|125000000.00|1.0
DTL|SRCA000001|20260428|E001|FIRST NAT BANK|1010|CASH AND DUE|RCON0010|5000000.00|CR|USD|093000|TXN-001|
DTL|SRCA000002|20260428|E001|FIRST NAT BANK|1020|INT BEARING BAL|RCON0071|1250000.00|CR|USD|093000|TXN-002|
DTL|SRCA000003|20260428|E001|FIRST NAT BANK|2010|TOTAL LOANS|RCON2122|87500000.00|DR|USD|093000|TXN-003|GROSS
DTL|SRCA000004|20260428|E001|FIRST NAT BANK|3010|TOTAL DEPOSITS|RCON2200|32000000.00|CR|USD|093000|TXN-004|
DTL|SRCA000005|20260428|E001|FIRST NAT BANK|4010|SECURITIES HTM|RCON1754|9800000.00|DR|USD|093000|TXN-005|
TRL|5|125550000.00|COMPLETE
```

---

*End of prompt. Provide this document to GitHub Copilot Chat using:*
*"Generate a complete Spring Boot application based on the following specification:"*
*then paste this entire file.*#   r e c o n _ a p p  
 