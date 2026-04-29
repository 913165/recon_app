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

/**
 * Indian Payment Matching Tests
 *
 * Each test validates one of the 4 match outcomes using Indian payment channels:
 *   UPI  — NPCI UPI Switch side     (channel A)
 *   IMPS — NPCI IMPS side           (channel B, counterpart in a cross-channel group)
 *   NEFT — RBI NEFT side
 *   RTGS — RBI RTGS side
 *
 * RCON codes used:
 *   RECON_UPI_CR   — UPI credit settlement total   (₹100 tolerance)
 *   RECON_NEFT_DR  — NEFT debit settlement total   (₹0 tolerance — exact)
 */
class ReconMatchingProcessorTest {

    private RconToleranceConfigRepository repository;
    private ReconMatchingProcessor processor;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(RconToleranceConfigRepository.class);

        // UPI credit: ₹100 tolerance (paise rounding allowed)
        RconToleranceConfig upiCfg = new RconToleranceConfig();
        upiCfg.setRconCode("RECON_UPI_CR");
        upiCfg.setTolerance(BigDecimal.valueOf(100));
        Mockito.when(repository.findByRconCode("RECON_UPI_CR")).thenReturn(Optional.of(upiCfg));

        // NEFT debit: zero tolerance (exact RBI gross settlement)
        RconToleranceConfig neftCfg = new RconToleranceConfig();
        neftCfg.setRconCode("RECON_NEFT_DR");
        neftCfg.setTolerance(BigDecimal.ZERO);
        Mockito.when(repository.findByRconCode("RECON_NEFT_DR")).thenReturn(Optional.of(neftCfg));

        Mockito.when(repository.findByRconCode(Mockito.argThat(code ->
                !code.equals("RECON_UPI_CR") && !code.equals("RECON_NEFT_DR"))))
                .thenReturn(Optional.empty());

        processor = new ReconMatchingProcessor(repository);
    }

    // ── Test 1: MATCHED — both UPI switch and bank CBS agree exactly ──────────
    @Test
    void upiExactAmountMatch_producesMatched() {
        // UPI switch says ₹15,00,000 credits; bank CBS also says ₹15,00,000
        var result = processor.processGroups(List.of(
                upiRecord(SourceSystem.UPI,  1_500_000, "RECON_UPI_CR"),
                upiRecord(SourceSystem.IMPS, 1_500_000, "RECON_UPI_CR")
        )).get(0);
        assertEquals(MatchStatus.MATCHED, result.getMatchStatus(),
                "Exact amounts within UPI tolerance should be MATCHED");
    }

    // ── Test 2: MATCHED — within ₹100 paise tolerance ────────────────────────
    @Test
    void upiWithinPaiseTolerance_producesMatched() {
        // ₹50 difference — within the ₹100 UPI paise-rounding tolerance
        var result = processor.processGroups(List.of(
                upiRecord(SourceSystem.UPI,  1_500_000, "RECON_UPI_CR"),
                upiRecord(SourceSystem.IMPS, 1_499_950, "RECON_UPI_CR")
        )).get(0);
        assertEquals(MatchStatus.MATCHED, result.getMatchStatus(),
                "₹50 difference is within ₹100 UPI tolerance");
    }

    // ── Test 3: BREAK — NEFT zero tolerance exceeded ─────────────────────────
    @Test
    void neftAmountMismatch_producesBreak() {
        // RBI NEFT: zero tolerance — any difference is a BREAK
        var result = processor.processGroups(List.of(
                upiRecord(SourceSystem.NEFT, 35_000_000, "RECON_NEFT_DR"),
                upiRecord(SourceSystem.RTGS, 34_950_000, "RECON_NEFT_DR")
        )).get(0);
        assertEquals(MatchStatus.BREAK, result.getMatchStatus(),
                "₹50,000 mismatch on NEFT (zero tolerance) should be BREAK");
    }

    // ── Test 4: PARTIAL — only one source provided data ───────────────────────
    @Test
    void singleSourceOnly_producesPartial() {
        // RTGS file arrived but bank CBS file for this RCON code is missing
        var result = processor.processGroups(List.of(
                upiRecord(SourceSystem.RTGS, 500_000_000, "RECON_RTGS_CR")
        )).get(0);
        assertEquals(MatchStatus.PARTIAL, result.getMatchStatus(),
                "Only RTGS side present — counterpart CBS missing — should be PARTIAL");
    }

    // ── Test 5: UPI tolerance exactly at boundary ─────────────────────────────
    @Test
    void upiAtExactToleranceBoundary_producesMatched() {
        // Exactly ₹100 difference = at tolerance boundary = MATCHED (<=)
        var result = processor.processGroups(List.of(
                upiRecord(SourceSystem.UPI,  1_500_000, "RECON_UPI_CR"),
                upiRecord(SourceSystem.IMPS, 1_499_900, "RECON_UPI_CR")
        )).get(0);
        assertEquals(MatchStatus.MATCHED, result.getMatchStatus(),
                "Difference exactly equal to tolerance should be MATCHED");
    }

    // ── Test 6: UPI tolerance exceeded by ₹1 → BREAK ─────────────────────────
    @Test
    void upiOneRupeeOverTolerance_producesBreak() {
        // ₹101 difference = just over ₹100 tolerance = BREAK
        var result = processor.processGroups(List.of(
                upiRecord(SourceSystem.UPI,  1_500_000, "RECON_UPI_CR"),
                upiRecord(SourceSystem.IMPS, 1_499_899, "RECON_UPI_CR")
        )).get(0);
        assertEquals(MatchStatus.BREAK, result.getMatchStatus(),
                "Difference just over tolerance should be BREAK");
    }

    private ValidatedRecord upiRecord(SourceSystem sourceSystem, long balanceInRupees, String rconCode) {
        return new ValidatedRecord(
                "F-INDIA-001",
                sourceSystem,
                "R-" + sourceSystem.name() + "-001",
                LocalDate.of(2026, 4, 29),
                "HDFC0001",
                rconCode,
                BigDecimal.valueOf(balanceInRupees),
                "INR"                          // Indian Rupee
        );
    }
}
