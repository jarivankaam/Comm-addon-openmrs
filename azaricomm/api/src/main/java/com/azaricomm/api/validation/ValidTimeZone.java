package com.azaricomm.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = TimeZoneValidator.class)
@Target({ ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidTimeZone {
    String message() default "Invalid time zone (must be a valid IANA zone like '+01:00')";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}