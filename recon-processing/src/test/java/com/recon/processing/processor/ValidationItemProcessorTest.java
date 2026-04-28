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
        assertEquals("RCON0010", validated.rconCode());
    }


    @Test
    void nullRconCodeFails() {
        // Create a row without setting rconCode (stays null via no-arg constructor)
        ReconStaging row = new ReconStaging();
        row.setFileId("F1");
        row.setSourceSystem(SourceSystem.CORE_BANKING);
        row.setRecordId("REC-NULL");
        row.setReportDate(LocalDate.now());
        row.setEntityId("E001");
        row.setBalance(BigDecimal.TEN);
        row.setDrCrInd(DrCrIndicator.DR);
        row.setCurrency("USD");
        // rconCode intentionally left null
        assertThrows(ValidationException.class, () -> processor.process(row));
    }

    @Test
    void duplicateRecordIdFails() {
        ReconStaging row = baseRow();
        row.setRecordId("REC-DUP");
        processor.process(row);
        // Second call with same record ID should be flagged as duplicate
        ReconStaging dup = baseRow();
        dup.setRecordId("REC-DUP");
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
        row.setFileId("F1");
        row.setSourceSystem(SourceSystem.CORE_BANKING);
        row.setRecordId("REC1");
        row.setReportDate(LocalDate.now());
        row.setEntityId("E001");
        row.setRconCode("RCON0010");
        row.setBalance(BigDecimal.TEN);
        row.setDrCrInd(DrCrIndicator.DR);
        row.setCurrency("USD");
        return row;
    }
}

