package com.recon.ingestion.parser;

import com.recon.common.enums.SourceSystem;
import com.recon.common.exception.ControlTotalMismatchException;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlatFileParserServiceTest {

    private final FlatFileParserService parserService = new FlatFileParserService();

    // ── UPI pipe-delimited .dat file ─────────────────────────────────────────
    @Test
    void parsesUpiPipeDelimitedFile() {
        var result = parserService.parse(
                Path.of("src/test/resources/sample/UPI_RECON_20260429_093000_001.dat"),
                SourceSystem.UPI);
        assertTrue(result.successfulRecords() > 0, "Should parse UPI records");
    }

    // ── IMPS pipe-delimited .dat file ────────────────────────────────────────
    @Test
    void parsesImpsFile() {
        var result = parserService.parse(
                Path.of("src/test/resources/sample/IMPS_RECON_20260429_090000_001.dat"),
                SourceSystem.IMPS);
        assertTrue(result.successfulRecords() > 0, "Should parse IMPS records");
    }

    // ── NEFT CSV file ────────────────────────────────────────────────────────
    @Test
    void parsesNeftCsvFile() {
        var result = parserService.parse(
                Path.of("src/test/resources/sample/NEFT_RECON_20260429_120000_001.csv"),
                SourceSystem.NEFT);
        assertTrue(result.successfulRecords() > 0, "Should parse NEFT records");
    }

    // ── RTGS pipe-delimited .dat file ────────────────────────────────────────
    @Test
    void parsesRtgsFile() {
        var result = parserService.parse(
                Path.of("src/test/resources/sample/RTGS_RECON_20260429_110000_001.dat"),
                SourceSystem.RTGS);
        assertTrue(result.successfulRecords() > 0, "Should parse RTGS records");
    }

    // ── Control total mismatch → exception ───────────────────────────────────
    @Test
    void throwsOnControlTotalMismatch() {
        assertThrows(ControlTotalMismatchException.class, () ->
                parserService.parse(
                        Path.of("src/test/resources/sample/SRCA_RECON_BAD_TOTAL.dat"),
                        SourceSystem.UPI));
    }

    // ── Malformed records → errors collected, does not abort ─────────────────
    @Test
    void capturesMalformedRecord() {
        var result = parserService.parse(
                Path.of("src/test/resources/sample/SRCA_RECON_MALFORMED.dat"),
                SourceSystem.UPI);
        assertFalse(result.failedRecords().isEmpty(), "Malformed rows should be in error list");
    }
}
