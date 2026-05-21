package com.azaricomm.api.service;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class EncryptionServiceTest {

    private static final String VALID_KEY = "12345678901234567890123456789012";

    @Test
    void constructor_WithInvalidKeyLength_ShouldThrowIllegalArgumentException() {
        String shortKey = "too-short";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new EncryptionService(shortKey);
        });

        assertTrue(exception.getMessage().contains("must be exactly 32 bytes"));
    }

    @Test
    void encrypt_WithValidInput_ShouldReturnBase64EncodedString() {
        EncryptionService service = new EncryptionService(VALID_KEY);
        String secretData = "Patient-Secret-123";

        String encrypted = service.encrypt(secretData);

        assertNotNull(encrypted);
        assertNotEquals(secretData, encrypted);
        assertDoesNotThrow(() -> Base64.getDecoder().decode(encrypted));
    }

    @Test
    void encrypt_ShouldUseUniqueIVEveryTime_ToPreventPatternMatching() {
        EncryptionService service = new EncryptionService(VALID_KEY);
        String secretData = "Same-Data-Every-Time";

        String firstRun = service.encrypt(secretData);
        String secondRun = service.encrypt(secretData);

        assertNotEquals(firstRun, secondRun, "Security Risk: Ciphertexts are identical! Unique IV is missing.");
    }

    @Test
    void encrypt_WithBlankOrNullInput_ShouldReturnNull() {
        EncryptionService service = new EncryptionService(VALID_KEY);

        assertNull(service.encrypt(null));
        assertNull(service.encrypt(""));
        assertNull(service.encrypt("   "));
    }
}