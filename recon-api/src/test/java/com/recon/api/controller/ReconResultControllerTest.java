package com.recon.api.controller;

import com.recon.api.service.ReconResultService;
import com.recon.common.dto.ReconResultDto;
import com.recon.common.dto.ReconSummaryDto;
import com.recon.common.dto.ResolveRequest;
import com.recon.common.enums.MatchStatus;
import com.recon.common.enums.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class ReconResultControllerTest {

    private ReconResultService service;
    private ReconResultController controller;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(ReconResultService.class);
        controller = new ReconResultController(service);
    }

    @Test
    void getResultsReturnsPage() {
        var dto = new ReconResultDto("R1", LocalDate.of(2026, 4, 28), "E001", "RCON0010", BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.valueOf(9), MatchStatus.BREAK, Severity.HIGH, false);
        Mockito.when(service.getResults(any(), any(), any(), any(), eq(0), eq(50))).thenReturn(new PageImpl<>(List.of(dto)));

        var page = controller.results("1", LocalDate.of(2026, 4, 28), "E001", MatchStatus.BREAK, Severity.HIGH, 0, 50);
        assertEquals(1, page.getTotalElements());
        assertEquals("R1", page.getContent().getFirst().reconId());
    }

    @Test
    void getSummaryReturnsAggregate() {
        Mockito.when(service.summary(LocalDate.of(2026, 4, 28)))
                .thenReturn(new ReconSummaryDto(LocalDate.of(2026, 4, 28), 100, 90, 5, 5, BigDecimal.valueOf(90), BigDecimal.TEN));

        var summary = controller.summary(LocalDate.of(2026, 4, 28));
        assertEquals(100, summary.totalRecords());
    }

    @Test
    void resolveUpdatesFlag() {
        var resolved = new ReconResultDto("R1", LocalDate.of(2026, 4, 28), "E001", "RCON0010", BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.valueOf(9), MatchStatus.BREAK, Severity.HIGH, true);
        Mockito.when(service.resolve(eq("R1"), any())).thenReturn(resolved);

        var response = controller.resolve("R1", new ResolveRequest("Timing difference confirmed"));
        assertTrue(response.resolved());
    }

    @Test
    void apiVersionHeaderIsAccepted() {
        Mockito.when(service.getResults(any(), any(), any(), any(), eq(0), eq(50))).thenReturn(new PageImpl<>(List.of()));

        var page = controller.results("1", LocalDate.of(2026, 4, 28), "E001", MatchStatus.BREAK, Severity.HIGH, 0, 50);
        assertEquals(0, page.getTotalElements());
    }
}
