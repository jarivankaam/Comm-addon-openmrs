package com.azaricomm.provider;

import com.azaricomm.model.DeliveryResult;
import com.azaricomm.model.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adapter for the SecurePost messaging provider.
 * Simulates a security-focused provider with encryption overhead.
 */
@Component
public class SecurePostProvider implements MessagingProvider {

    private static final Logger log = LoggerFactory.getLogger(SecurePostProvider.class);

    @Override
    public String getName() {
        return "securepost";
    }

    @Override
    public DeliveryResult send(NotificationMessage message) {
        log.info("[SecurePost] Encrypting and sending to {} | subject: {} | body: {}",
                message.getPatientPhone(), message.getSubject(), message.getBody());

        // Simulate encryption + delivery overhead
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String messageId = "SP-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("[SecurePost] Delivered securely, messageId={}", messageId);
        return DeliveryResult.success(getName(), messageId);
    }
}
