package com.recon.common.validation;

import com.recon.common.annotation.ValidRconCode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class RconCodeValidator implements ConstraintValidator<ValidRconCode, String> {

    /**
     * Accepts:
     *   RCON\d{4}                              — legacy format (e.g. RCON0010)
     *   RECON_(UPI|IMPS|NEFT|RTGS)_(CR|DR|REV|CHB|RET|REJ)  — Indian payment channels
     */
    private static final Pattern PATTERN =
            Pattern.compile("^(RCON\\d{4}|RECON_(UPI|IMPS|NEFT|RTGS)_(CR|DR|REV|CHB|RET|REJ))$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value != null && PATTERN.matcher(value).matches();
    }
}

