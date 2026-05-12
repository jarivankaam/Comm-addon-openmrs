package com.azaricomm.model;

/**
 * Result of a delivery attempt by a messaging provider.
 */
public class DeliveryResult {

    private final boolean success;
    private final String providerName;
    private final String providerMessageId;
    private final String errorMessage;

    private DeliveryResult(boolean success, String providerName, String providerMessageId, String errorMessage) {
        this.success = success;
        this.providerName = providerName;
        this.providerMessageId = providerMessageId;
        this.errorMessage = errorMessage;
    }

    public static DeliveryResult success(String providerName, String providerMessageId) {
        return new DeliveryResult(true, providerName, providerMessageId, null);
    }

    public static DeliveryResult failure(String providerName, String errorMessage) {
        return new DeliveryResult(false, providerName, null, errorMessage);
    }

    public boolean isSuccess() { return success; }
    public String getProviderName() { return providerName; }
    public String getProviderMessageId() { return providerMessageId; }
    public String getErrorMessage() { return errorMessage; }

    @Override
    public String toString() {
        if (success) {
            return "DeliveryResult{SUCCESS, provider=" + providerName + ", messageId=" + providerMessageId + "}";
        }
        return "DeliveryResult{FAILED, provider=" + providerName + ", error=" + errorMessage + "}";
    }
}
