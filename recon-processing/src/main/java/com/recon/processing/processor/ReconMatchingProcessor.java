package com.recon.processing.processor;

import com.recon.common.dto.ValidatedRecord;
import com.recon.common.enums.MatchStatus;
import com.recon.common.enums.Severity;
import com.recon.common.enums.SourceSystem;
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

    private ReconResult matchGroup(List<ValidatedRecord> group) {
        ValidatedRecord srcA = group.stream().filter(r -> r.sourceSystem() == SourceSystem.CORE_BANKING).findFirst().orElse(null);
        ValidatedRecord srcB = group.stream().filter(r -> r.sourceSystem() == SourceSystem.LOANS_SYS).findFirst().orElse(null);
        ValidatedRecord srcC = group.stream().filter(r -> r.sourceSystem() == SourceSystem.TRADING_GL).findFirst().orElse(null);

        ValidatedRecord baseline = srcA != null ? srcA : (srcB != null ? srcB : srcC);
        if (baseline == null) {
            throw new IllegalStateException("Cannot match empty group");
        }

        BigDecimal tolerance = toleranceConfigRepository.findByRconCode(baseline.rconCode())
                .map(config -> config.getTolerance())
                .orElse(BigDecimal.ZERO);

        // Counterpart: something other than the baseline source
        ValidatedRecord counterpart;
        if (baseline == srcA) {
            counterpart = srcB != null ? srcB : srcC;  // CORE_BANKING vs. LOANS or TRADING
        } else {
            counterpart = srcA;  // non-CORE_BANKING baseline: counterpart is CORE_BANKING (may be null → PARTIAL)
        }
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
        if (b == null && a.sourceSystem() != SourceSystem.CORE_BANKING) {
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
