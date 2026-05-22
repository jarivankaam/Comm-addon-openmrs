package com.azaricomm.api.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.ZoneOffset;

public class TimeZoneValidator implements ConstraintValidator<ValidTimeZone, String> {

    @Override
    public boolean isValid(String timeZoneStr, ConstraintValidatorContext context) {
        if (timeZoneStr == null || timeZoneStr.isBlank()) {
            return true; // @NotBlank will handle this.
        }

        try {
            // try to match the zone with java timezone. If it fails, it isnt the right format.
            ZoneOffset.of(timeZoneStr);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}