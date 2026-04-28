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

    @Test
    void parsesPipeDelimitedFile() {
        var result = parserService.parse(Path.of("src/test/resources/sample/SRCA_RECON_20260428_093000_001.dat"), SourceSystem.CORE_BANKING);
        assertTrue(result.successfulRecords() > 0);
    }

    @Test
    void parsesCsvFile() {
        var result = parserService.parse(Path.of("src/test/resources/sample/sample.csv"), SourceSystem.LOANS_SYS);
        assertTrue(result.successfulRecords() > 0);
    }

    @Test
    void parsesFixedWidthLegacyFile() {
        var result = parserService.parse(Path.of("src/test/resources/sample/sample_legacy.txt"), SourceSystem.TRADING_GL);
        assertTrue(result.totalRecords() > 0);
    }

    @Test
    void throwsOnControlTotalMismatch() {
        assertThrows(ControlTotalMismatchException.class, () ->
                parserService.parse(Path.of("src/test/resources/sample/SRCA_RECON_BAD_TOTAL.dat"), SourceSystem.CORE_BANKING));
    }

    @Test
    void capturesMalformedRecord() {
        var result = parserService.parse(Path.of("src/test/resources/sample/SRCA_RECON_MALFORMED.dat"), SourceSystem.CORE_BANKING);
        assertFalse(result.failedRecords().isEmpty());
    }
}

