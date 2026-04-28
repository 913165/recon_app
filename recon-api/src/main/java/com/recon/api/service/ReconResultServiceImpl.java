package com.recon.api.service;

import com.recon.api.mapper.ReconResultMapper;
import com.recon.common.dto.ReconAuditDto;
import com.recon.common.dto.ReconResultDto;
import com.recon.common.dto.ReconSummaryDto;
import com.recon.common.enums.MatchStatus;
import com.recon.common.enums.Severity;
import com.recon.storage.entity.ReconResult;
import com.recon.storage.repository.ReconResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReconResultServiceImpl implements ReconResultService {

    private final ReconResultRepository reconResultRepository;
    private final ReconResultMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public Page<ReconResultDto> getResults(LocalDate reportDate, String entityId, MatchStatus status, Severity severity, int page, int size) {
        var pageable = PageRequest.of(page, size);
        var entities = reconResultRepository.findByReportDateAndEntityIdAndMatchStatusAndSeverity(
                reportDate,
                entityId,
                status,
                severity,
                pageable
        );
        return new PageImpl<>(entities.stream().map(mapper::toDto).toList(), pageable, entities.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public ReconResultDto getByReconId(String reconId) {
        return mapper.toDto(reconResultRepository.findByReconId(reconId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown reconId: " + reconId)));
    }

    @Override
    @Transactional
    public ReconResultDto resolve(String reconId, String resolutionNote) {
        ReconResult entity = reconResultRepository.findByReconId(reconId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown reconId: " + reconId));
        entity.setResolved(true);
        entity.setBreakReason(resolutionNote);
        entity.setUpdatedAt(OffsetDateTime.now());
        return mapper.toDto(reconResultRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public ReconSummaryDto summary(LocalDate reportDate) {
        long total = reconResultRepository.countByReportDate(reportDate);
        long matched = reconResultRepository.countByReportDateAndMatchStatus(reportDate, MatchStatus.MATCHED);
        long breaks = reconResultRepository.countByReportDateAndMatchStatus(reportDate, MatchStatus.BREAK);
        long unmatched = reconResultRepository.countByReportDateAndMatchStatus(reportDate, MatchStatus.UNMATCHED);
        BigDecimal rate = total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(matched)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

        BigDecimal totalBreakAmount = reconResultRepository.findAll().stream()
                .filter(r -> reportDate.equals(r.getReportDate()) && r.getMatchStatus() == MatchStatus.BREAK)
                .map(r -> (r.getBalanceA() == null || r.getBalanceB() == null)
                        ? BigDecimal.ZERO
                        : r.getBalanceA().subtract(r.getBalanceB()).abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ReconSummaryDto(reportDate, total, matched, breaks, unmatched, rate, totalBreakAmount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReconAuditDto> history(String reconId) {
        return List.of(new ReconAuditDto(reconId, "RESOLVED", "History table integration pending", OffsetDateTime.now()));
    }
}

