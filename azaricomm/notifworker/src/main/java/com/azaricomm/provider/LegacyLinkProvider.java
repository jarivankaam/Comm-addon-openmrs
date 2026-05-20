package com.azaricomm.provider;

import com.azaricomm.model.DeliveryResult;
import com.azaricomm.model.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adapter for the LegacyLink messaging provider.
 * Simulates a slower legacy API with occasional timeouts.
 */
@Component
public class LegacyLinkProvider implements MessagingProvider {

    private static final Logger log = LoggerFactory.getLogger(LegacyLinkProvider.class);

    @Override
    public String getName() {
        return "legacylink";
    }

    @Override
    public DeliveryResult send(NotificationMessage message) {
        log.info("[LegacyLink] Sending to {} | subject: {} | body: {}",
                message.getPatientPhone(), message.getSubject(), message.getBody());

        // Simulate slower API
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String messageId = "LL-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("[LegacyLink] Delivered successfully, messageId={}", messageId);
        return DeliveryResult.success(getName(), messageId);
    }
}
