package com.azaricomm.api.webhook;

public interface WebhookAuthenticator {
    boolean isValid(String payload, String signature);
}
