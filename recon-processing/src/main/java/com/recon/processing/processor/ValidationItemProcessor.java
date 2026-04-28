package com.recon.processing.processor;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.recon.common.dto.ValidatedRecord;
import com.recon.common.enums.DrCrIndicator;
import com.recon.storage.entity.ReconStaging;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Currency;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class ValidationItemProcessor {

    private static final Pattern RCON_PATTERN = Pattern.compile("^RCON\\d{4}$");
    private final BloomFilter<CharSequence> dupFilter =
            BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), 5_000_000);

    private final Validator validator;

    public ValidatedRecord process(ReconStaging item) {
        if (item.getRconCode() == null || !RCON_PATTERN.matcher(item.getRconCode()).matches()) {
            throw new ValidationException("Invalid RCON code: " + item.getRconCode());
        }
        if (item.getBalance() == null || item.getBalance().signum() < 0) {
            throw new ValidationException("Balance cannot be negative");
        }
        if (!isIsoCurrency(item.getCurrency())) {
            throw new ValidationException("Invalid currency: " + item.getCurrency());
        }
        if (item.getDrCrInd() != DrCrIndicator.DR && item.getDrCrInd() != DrCrIndicator.CR) {
            throw new ValidationException("Invalid DR/CR indicator");
        }
        if (dupFilter.mightContain(item.getRecordId())) {
            throw new ValidationException("Duplicate record detected: " + item.getRecordId());
        }
        dupFilter.put(item.getRecordId());

        ValidatedRecord validated = new ValidatedRecord(
                item.getFileId(),
                item.getSourceSystem(),
                item.getRecordId(),
                item.getReportDate(),
                item.getEntityId(),
                item.getRconCode(),
                item.getBalance(),
                item.getCurrency()
        );

        Set violations = validator.validate(validated);
        if (!violations.isEmpty()) {
            log.warn("Validation violations detected for record {}", item.getRecordId());
            throw new ValidationException(violations.iterator().next().toString());
        }
        return validated;
    }

    private boolean isIsoCurrency(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        try {
            Currency.getInstance(code);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
