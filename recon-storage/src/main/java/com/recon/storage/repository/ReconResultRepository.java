package com.recon.storage.repository;

import com.recon.common.enums.MatchStatus;
import com.recon.common.enums.Severity;
import com.recon.storage.entity.ReconResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ReconResultRepository extends JpaRepository<ReconResult, Long> {
    Optional<ReconResult> findByReconId(String reconId);

    Page<ReconResult> findByReportDateAndEntityIdAndMatchStatusAndSeverity(
            LocalDate reportDate,
            String entityId,
            MatchStatus matchStatus,
            Severity severity,
            Pageable pageable
    );

    long countByReportDate(LocalDate reportDate);

    long countByReportDateAndMatchStatus(LocalDate reportDate, MatchStatus matchStatus);
}

