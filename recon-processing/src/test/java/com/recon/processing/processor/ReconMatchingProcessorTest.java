package com.recon.processing.processor;

import com.recon.common.dto.ValidatedRecord;
import com.recon.common.enums.MatchStatus;
import com.recon.common.enums.SourceSystem;
import com.recon.storage.entity.RconToleranceConfig;
import com.recon.storage.repository.RconToleranceConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReconMatchingProcessorTest {

    private RconToleranceConfigRepository repository;
    private ReconMatchingProcessor processor;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(RconToleranceConfigRepository.class);
        RconToleranceConfig cfg = new RconToleranceConfig();
        cfg.setRconCode("RCON0010");
        cfg.setTolerance(BigDecimal.valueOf(100));
        Mockito.when(repository.findByRconCode("RCON0010")).thenReturn(Optional.of(cfg));
        processor = new ReconMatchingProcessor(repository);
    }

    @Test
    void matchingRecordsProduceMatched() {
        var result = processor.processGroups(List.of(record(SourceSystem.CORE_BANKING, 1000), record(SourceSystem.LOANS_SYS, 1000))).get(0);
        assertEquals(MatchStatus.MATCHED, result.getMatchStatus());
    }

    @Test
    void withinToleranceProducesMatched() {
        var result = processor.processGroups(List.of(record(SourceSystem.CORE_BANKING, 1000), record(SourceSystem.LOANS_SYS, 950))).get(0);
        assertEquals(MatchStatus.MATCHED, result.getMatchStatus());
    }

    @Test
    void exceedingToleranceProducesBreak() {
        var result = processor.processGroups(List.of(record(SourceSystem.CORE_BANKING, 1000), record(SourceSystem.LOANS_SYS, 500))).get(0);
        assertEquals(MatchStatus.BREAK, result.getMatchStatus());
    }

    @Test
    void missingCounterpartProducesUnmatched() {
        var result = processor.processGroups(List.of(record(SourceSystem.CORE_BANKING, 1000))).get(0);
        assertEquals(MatchStatus.UNMATCHED, result.getMatchStatus());
    }

    @Test
    void missingCoreBankingProducesPartial() {
        var result = processor.processGroups(List.of(record(SourceSystem.LOANS_SYS, 1000))).get(0);
        assertEquals(MatchStatus.PARTIAL, result.getMatchStatus());
    }

    private ValidatedRecord record(SourceSystem sourceSystem, int balance) {
        return new ValidatedRecord("F1", sourceSystem, "R1", LocalDate.now(), "E001", "RCON0010", BigDecimal.valueOf(balance), "USD");
    }
}

