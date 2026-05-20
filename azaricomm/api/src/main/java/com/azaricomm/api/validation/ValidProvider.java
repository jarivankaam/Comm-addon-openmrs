package com.azaricomm.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ProviderValidator.class)
@Target({ ElementType.FIELD })
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidProvider {
    String message() default "Provider is invalid, inactive or not supported";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}