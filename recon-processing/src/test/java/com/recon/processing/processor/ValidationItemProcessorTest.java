package com.recon.processing.processor;

import com.recon.common.enums.DrCrIndicator;
import com.recon.common.enums.SourceSystem;
import com.recon.storage.entity.ReconStaging;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidationItemProcessorTest {

    private final ValidationItemProcessor processor = new ValidationItemProcessor(Validation.buildDefaultValidatorFactory().getValidator());

    @Test
    void validRecordPasses() {
        ReconStaging row = baseRow();
        var validated = processor.process(row);
        assertEquals("RECON_UPI_CR", validated.rconCode());
    }


    @Test
    void nullRconCodeFails() {
        ReconStaging row = new ReconStaging();
        row.setFileId("UPI_20260429_001");
        row.setSourceSystem(SourceSystem.UPI);
        row.setRecordId("UPI-NULL");
        row.setReportDate(LocalDate.now());
        row.setEntityId("HDFC0001");
        row.setBalance(BigDecimal.TEN);
        row.setDrCrInd(DrCrIndicator.CR);
        row.setCurrency("INR");
        // rconCode intentionally left null
        assertThrows(ValidationException.class, () -> processor.process(row));
    }

    @Test
    void duplicateRecordIdFails() {
        ReconStaging row = baseRow();
        row.setRecordId("UPI-DUP-001");
        processor.process(row);
        ReconStaging dup = baseRow();
        dup.setRecordId("UPI-DUP-001");
        assertThrows(ValidationException.class, () -> processor.process(dup));
    }

    @Test
    void negativeBalanceFails() {
        ReconStaging row = baseRow();
        row.setBalance(BigDecimal.valueOf(-1));
        assertThrows(ValidationException.class, () -> processor.process(row));
    }

    private ReconStaging baseRow() {
        ReconStaging row = new ReconStaging();
        row.setFileId("UPI_20260429_001");
        row.setSourceSystem(SourceSystem.UPI);
        row.setRecordId("UPI000001");
        row.setReportDate(LocalDate.of(2026, 4, 29));
        row.setEntityId("HDFC0001");
        row.setRconCode("RECON_UPI_CR");
        row.setBalance(BigDecimal.valueOf(15_000_000));
        row.setDrCrInd(DrCrIndicator.CR);
        row.setCurrency("INR");
        return row;
    }
}

