package com.recon.api.service;

import com.recon.common.dto.ReconAuditDto;
import com.recon.common.dto.ReconResultDto;
import com.recon.common.dto.ReconSummaryDto;
import com.recon.common.enums.MatchStatus;
import com.recon.common.enums.Severity;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

public interface ReconResultService {
    Page<ReconResultDto> getResults(LocalDate reportDate, String entityId, MatchStatus status, Severity severity, int page, int size);

    ReconResultDto getByReconId(String reconId);

    ReconResultDto resolve(String reconId, String resolutionNote);

    ReconSummaryDto summary(LocalDate reportDate);

    List<ReconAuditDto> history(String reconId);
}

