package com.azaricomm.api.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Component
public class HmacWebhookAuthenticator implements WebhookAuthenticator {

    private final String webhookSecret;

    public HmacWebhookAuthenticator(@Value("${webhook.secret:}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    @Override
    public boolean isValid(String payload, String signature) {
        if (webhookSecret.isEmpty()) return true;
        String expected = "sha256=" + hmacSha256(payload, webhookSecret);
        return expected.equals(signature);
    }

    private static String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC error", e);
        }
    }
}
