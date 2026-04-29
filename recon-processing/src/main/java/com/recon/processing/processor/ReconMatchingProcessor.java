package com.recon.processing.processor;

import com.recon.common.dto.ValidatedRecord;
import com.recon.common.enums.MatchStatus;
import com.recon.common.enums.Severity;
import com.recon.storage.entity.ReconResult;
import com.recon.storage.repository.RconToleranceConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReconMatchingProcessor {

    private final RconToleranceConfigRepository toleranceConfigRepository;

    public ReconResult process(ValidatedRecord item) {
        BigDecimal tolerance = toleranceConfigRepository.findByRconCode(item.rconCode())
                .map(config -> config.getTolerance())
                .orElse(BigDecimal.ZERO);
        // Step-level processor receives single records; grouping helper is exposed for service/test use.
        return buildResult(item, null, tolerance);
    }

    public List<ReconResult> processGroups(List<ValidatedRecord> records) {
        Map<String, List<ValidatedRecord>> grouped = new ConcurrentHashMap<>();
        records.forEach(record -> grouped.computeIfAbsent(key(record), k -> new ArrayList<>()).add(record));

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            return grouped.values().stream()
                    .map(group -> executor.submit(() -> matchGroup(group)))
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception ex) {
                            throw new IllegalStateException("Matching failed", ex);
                        }
                    })
                    .toList();
        }
    }

    /**
     * Indian Payment Matching Logic
     * ─────────────────────────────
     * Each payment channel reconciles its NPCI/RBI switch record (side A)
     * against the bank's own CBS (Core Banking System) record (side B).
     *
     * Matching pairs by payment type:
     *   UPI  → NPCI UPI Switch  vs  bank CBS settlement
     *   IMPS → NPCI IMPS Switch vs  bank CBS settlement
     *   NEFT → RBI NEFT batch   vs  bank CBS settlement
     *   RTGS → RBI RTGS gross   vs  bank CBS settlement
     *
     * Within a group (same date + entityId + rconCode):
     *   - First record found = baseline (switch/NPCI/RBI side)
     *   - Second record      = counterpart (bank CBS side)
     *   - Only one record    = UNMATCHED or PARTIAL
     */
    private ReconResult matchGroup(List<ValidatedRecord> group) {
        // For Indian payments we always take the first record as baseline (switch side)
        // and the second as counterpart (bank CBS side).
        // If only one source system is present in the group it means the counterpart
        // file has not yet arrived → UNMATCHED / PARTIAL.
        ValidatedRecord baseline = group.stream()
                .min(java.util.Comparator.comparing(r -> r.sourceSystem().name()))
                .orElseThrow(() -> new IllegalStateException("Cannot match empty group"));

        ValidatedRecord counterpart = group.stream()
                .filter(r -> r.sourceSystem() != baseline.sourceSystem())
                .findFirst()
                .orElse(null);

        BigDecimal tolerance = toleranceConfigRepository.findByRconCode(baseline.rconCode())
                .map(config -> config.getTolerance())
                .orElse(BigDecimal.ZERO);

        return buildResult(baseline, counterpart, tolerance);
    }

    private ReconResult buildResult(ValidatedRecord a, ValidatedRecord b, BigDecimal tolerance) {
        BigDecimal balanceA = a.balance();
        BigDecimal balanceB = b == null ? null : b.balance();
        BigDecimal difference = balanceB == null ? balanceA : balanceA.subtract(balanceB);

        MatchStatus status;
        if (b == null) {
            status = MatchStatus.UNMATCHED;
        } else if (difference.abs().compareTo(tolerance) <= 0) {
            status = MatchStatus.MATCHED;
        } else {
            status = MatchStatus.BREAK;
        }

        Severity severity = toSeverity(difference.abs());

        // PARTIAL: only one side arrived (the counterpart source system has no data for this period)
        if (b == null) {
            status = MatchStatus.PARTIAL;
        }

        return ReconResult.builder()
                .reconId(a.fileId() + "-" + a.recordId())
                .reportDate(a.reportDate())
                .entityId(a.entityId())
                .rconCode(a.rconCode())
                .sourceSystemA(a.sourceSystem().name())
                .balanceA(balanceA)
                .sourceSystemB(b == null ? "N/A" : b.sourceSystem().name())
                .balanceB(balanceB)
                .tolerance(tolerance)
                .matchStatus(status)
                .severity(severity)
                .breakReason(status == MatchStatus.BREAK ? "Difference exceeds configured tolerance" : null)
                .resolved(Boolean.FALSE)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private Severity toSeverity(BigDecimal absDifference) {
        if (absDifference.compareTo(BigDecimal.valueOf(1_000)) < 0) {
            return Severity.LOW;
        }
        if (absDifference.compareTo(BigDecimal.valueOf(10_000)) < 0) {
            return Severity.MEDIUM;
        }
        if (absDifference.compareTo(BigDecimal.valueOf(100_000)) < 0) {
            return Severity.HIGH;
        }
        return Severity.CRITICAL;
    }

    private String key(ValidatedRecord record) {
        return record.reportDate() + "|" + record.entityId() + "|" + record.rconCode();
    }
}
