package com.recon.api.controller;

import com.recon.api.service.ReconResultService;
import com.recon.common.dto.ReconAuditDto;
import com.recon.common.dto.ReconResultDto;
import com.recon.common.dto.ReconSummaryDto;
import com.recon.common.dto.ResolveRequest;
import com.recon.common.enums.MatchStatus;
import com.recon.common.enums.Severity;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/recon")
@RequiredArgsConstructor
public class ReconResultController {

    private final ReconResultService reconResultService;

    @Operation(summary = "Get paged reconciliation results")
    @GetMapping("/results")
    public Page<ReconResultDto> results(
            @RequestHeader(value = "API-Version", required = false, defaultValue = "1") String apiVersion,
            @RequestParam LocalDate reportDate,
            @RequestParam String entityId,
            @RequestParam MatchStatus status,
            @RequestParam Severity severity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return reconResultService.getResults(reportDate, entityId, status, severity, page, size);
    }

    @Operation(summary = "Get one reconciliation result")
    @GetMapping("/results/{reconId}")
    public ReconResultDto getById(@PathVariable String reconId) {
        return reconResultService.getByReconId(reconId);
    }

    @Operation(summary = "Resolve a break")
    @PatchMapping("/results/{reconId}/resolve")
    public ReconResultDto resolve(@PathVariable String reconId, @Valid ResolveRequest request) {
        return reconResultService.resolve(reconId, request.resolutionNote());
    }

    @Operation(summary = "Get reconciliation daily summary")
    @GetMapping("/summary")
    public ReconSummaryDto summary(@RequestParam LocalDate reportDate) {
        return reconResultService.summary(reportDate);
    }

    @Operation(summary = "Get reconciliation audit history")
    @GetMapping("/results/{reconId}/history")
    public List<ReconAuditDto> history(@PathVariable String reconId) {
        return reconResultService.history(reconId);
    }
}

