package com.azaricomm.consumer;

import com.azaricomm.model.DeliveryResult;
import com.azaricomm.model.NotificationMessage;
import com.azaricomm.provider.ProviderRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final ProviderRouter providerRouter;
    private final ObjectMapper objectMapper;

    public NotificationConsumer(ProviderRouter providerRouter) {
        this.providerRouter = providerRouter;
        this.objectMapper = new ObjectMapper();
    }

    @RabbitListener(queues = "${rabbitmq.queue:azaricomm.notifications}")
    public void handleNotification(String messageBody) {
        log.info("Received notification from queue");

        try {
            NotificationMessage message = objectMapper.readValue(messageBody, NotificationMessage.class);
            log.info("Parsed notification: {}", message);

            DeliveryResult result = providerRouter.route(message);

            if (result.isSuccess()) {
                log.info("Notification delivered: {}", result);
            } else {
                log.error("Notification delivery failed: {}", result);
                // TODO: implement retry mechanism (sprint 3)
            }

        } catch (Exception e) {
            log.error("Failed to process notification: {}", e.getMessage(), e);
        }
    }
}
