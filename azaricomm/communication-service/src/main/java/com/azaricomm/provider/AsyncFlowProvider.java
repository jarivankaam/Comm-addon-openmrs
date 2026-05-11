package com.azaricomm.provider;

import com.azaricomm.model.DeliveryResult;
import com.azaricomm.model.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Adapter for the AsyncFlow messaging provider.
 * Simulates an asynchronous API that accepts immediately and delivers later.
 */
@Component
public class AsyncFlowProvider implements MessagingProvider {

    private static final Logger log = LoggerFactory.getLogger(AsyncFlowProvider.class);

    @Override
    public String getName() {
        return "asyncflow";
    }

    @Override
    public DeliveryResult send(NotificationMessage message) {
        log.info("[AsyncFlow] Queuing message for {} | subject: {} | body: {}",
                message.getPatientPhone(), message.getSubject(), message.getBody());

        // Simulate fast acceptance (async provider accepts immediately)
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String messageId = "AF-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("[AsyncFlow] Accepted for delivery, messageId={}", messageId);
        return DeliveryResult.success(getName(), messageId);
    }
}
