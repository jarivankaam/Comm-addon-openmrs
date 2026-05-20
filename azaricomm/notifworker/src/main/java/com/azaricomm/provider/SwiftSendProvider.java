package com.azaricomm.provider;

import com.azaricomm.model.DeliveryResult;
import com.azaricomm.model.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adapter for the SwiftSend messaging provider.
 * Simulates a fast synchronous REST API.
 */
@Component
public class SwiftSendProvider implements MessagingProvider {

    private static final Logger log = LoggerFactory.getLogger(SwiftSendProvider.class);

    @Override
    public String getName() {
        return "swiftsend";
    }

    @Override
    public DeliveryResult send(NotificationMessage message) {
        log.info("[SwiftSend] Sending to {} | subject: {} | body: {}",
                message.getPatientPhone(), message.getSubject(), message.getBody());

        // Simulate API call
        try {
            Thread.sleep(100); // fast provider
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String messageId = "SS-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("[SwiftSend] Delivered successfully, messageId={}", messageId);
        return DeliveryResult.success(getName(), messageId);
    }
}
