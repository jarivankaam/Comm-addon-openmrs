package com.azaricomm.consumer;

import com.azaricomm.model.DeliveryResult;
import com.azaricomm.model.NotificationMessage;
import com.azaricomm.provider.ProviderRouter;
import com.azaricomm.service.AppointmentEnrichmentService;
import com.azaricomm.service.NotificationValidationService;
import com.azaricomm.service.NotificationRetryService;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ProviderRouter providerRouter;
    private final NotificationValidationService validationService;
    private final NotificationRetryService retryService;
    private final AppointmentEnrichmentService enrichmentService;

    public NotificationConsumer(ProviderRouter providerRouter,
                               NotificationValidationService validationService,
                               NotificationRetryService retryService,
                               AppointmentEnrichmentService enrichmentService) {
        this.providerRouter = providerRouter;
        this.validationService = validationService;
        this.retryService = retryService;
        this.enrichmentService = enrichmentService;
    }

    @RabbitListener(queues = "${rabbitmq.queue:azaricomm.notifications}")
    public void handleNotification(String messageBody) {
        log.info("Received notification from queue");

        NotificationMessage message = null;
        try {
            message = objectMapper.readValue(messageBody, NotificationMessage.class);
            log.info("Parsed notification: {}", message);

            // Enrich message with full appointment data from MongoDB
            if (!enrichmentService.enrich(message)) {
                log.warn("Could not enrich notification, discarding message for appointment: {}", message.getAppointmentId());
                return;
            }

            // Validate message before routing
            if (!validationService.validateMessage(message)) {
                log.warn("Notification validation failed, discarding message: {}", message);
                return;
            }

            // Route to appropriate provider
            DeliveryResult result = providerRouter.route(message);

            if (result.isSuccess()) {
                log.info("Notification delivered: {}", result);
                // Mark as sent ONLY after successful delivery
                retryService.markNotificationAsSent(message.getAppointmentId(), message.getNotificationType());
            } else {
                log.error("Notification delivery failed: {}", result);
                // Save for retry with exponential backoff (10s, 1m, 1h)
                retryService.saveFailedNotification(
                    message.getAppointmentId(),
                    message.getNotificationType(),
                    result.getErrorMessage()
                );
            }

        } catch (JsonParseException | JsonMappingException e) {
            log.error("Failed to parse notification JSON from message: {}", messageBody, e);
        } catch (Exception e) {
            log.error("Failed to process notification - Message: {} Error: {}", messageBody, e.getMessage(), e);
        }
    }
}
