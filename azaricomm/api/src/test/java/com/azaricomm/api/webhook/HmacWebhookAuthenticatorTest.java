package com.azaricomm.api.webhook;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import static org.junit.jupiter.api.Assertions.*;

class HmacWebhookAuthenticatorTest {

    @Test
    void isValid_WithValidSignature_ShouldReturnTrue() {
        String secret = "super-secret-webhook-key";
        HmacWebhookAuthenticator authenticator = new HmacWebhookAuthenticator(secret);
        String payload = "{\"event\":\"appointment_created\"}";

        String validHmac = generateExpectedHmac(payload, secret);
        String validSignatureHeader = "sha256=" + validHmac;

        assertTrue(authenticator.isValid(payload, validSignatureHeader),
                "Een legitieme webhook met de juiste handtekening moet worden geaccepteerd.");
    }

    @Test
    void isValid_WithInvalidSignature_ShouldReturnFalse() {
        HmacWebhookAuthenticator authenticator = new HmacWebhookAuthenticator("secret");

        assertFalse(authenticator.isValid("payload", "sha256=wrongsignature"));
    }

    @Test
    void isValid_WhenSecretIsEmpty_ShouldAlwaysReturnTrue() {
        HmacWebhookAuthenticator authenticator = new HmacWebhookAuthenticator("");
        assertTrue(authenticator.isValid("any-payload", "any-signature"));
    }
    
    private String generateExpectedHmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}