package com.azaricomm.api.validation;

import com.azaricomm.api.repository.ProviderRepository;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;

public class ProviderValidator implements ConstraintValidator<ValidProvider, String> {

    @Autowired
    private ProviderRepository providerRepository;

    @Override
    public boolean isValid(String providerId, ConstraintValidatorContext context) {
        // If empty, @NotBlank will handle it.
        if (providerId == null || providerId.isBlank()) {
            return true;
        }

        // Check if the provider does exist (and if it is active) in the MongoDB.
        return providerRepository.existsByIdAndIsActiveTrue(providerId);
    }
}