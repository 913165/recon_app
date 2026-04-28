package com.recon.common.annotation;

import com.recon.common.validation.RconCodeValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RconCodeValidator.class)
public @interface ValidRconCode {
    String message() default "Invalid RCON code. Expected RCON followed by 4 digits.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

