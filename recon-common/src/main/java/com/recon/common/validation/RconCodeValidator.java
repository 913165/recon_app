package com.recon.common.validation;

import com.recon.common.annotation.ValidRconCode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class RconCodeValidator implements ConstraintValidator<ValidRconCode, String> {

    private static final Pattern PATTERN = Pattern.compile("^RCON\\d{4}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value != null && PATTERN.matcher(value).matches();
    }
}

