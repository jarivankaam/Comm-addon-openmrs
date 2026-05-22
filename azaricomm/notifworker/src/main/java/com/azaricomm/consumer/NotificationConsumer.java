package com.azaricomm.consumer;

import com.azaricomm.model.DeliveryResult;
import com.azaricomm.model.NotificationMessage;
import com.azaricomm.provider.ProviderRouter;
import com.azaricomm.service.AppointmentEnrichmentService;
import com.azaricomm.service.NotificationRetryService;
import com.azaricomm.service.NotificationValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);
    private static final int MAX_RETRIES = 3;

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
    public void handleNotification(NotificationMessage message, @Headers Map<String, Object> headers) {
        log.info("Received notification: {}", message);

        if (!enrichmentService.enrich(message)) {
            log.warn("Could not enrich notification, discarding message for appointment: {}", message.getAppointmentId());
            return;
        }

        if (!validationService.validateMessage(message)) {
            log.warn("Notification validation failed, discarding message: {}", message);
            return;
        }

        String currentStatus = retryService.getNotificationStatus(message.getAppointmentId(), message.getNotificationType());
        if ("SENT".equals(currentStatus)) {
            log.warn("[Idempotency] Notification already sent, discarding redelivered message for appointmentId={} type={}",
                    message.getAppointmentId(), message.getNotificationType());
            return;
        }

        DeliveryResult result = providerRouter.route(message);

        if (result.isSuccess()) {
            log.info("Notification delivered: {}", result);
            retryService.markNotificationAsSent(message.getAppointmentId(), message.getNotificationType());
            return;
        }

        long retryCount = getRetryCount(headers);
        log.error("Notification delivery failed (attempt {}): {}", retryCount + 1, result);

        if (retryCount >= MAX_RETRIES) {
            log.error("[Retry] Max retries ({}) exceeded for appointmentId={} type={}, giving up",
                    MAX_RETRIES, message.getAppointmentId(), message.getNotificationType());
            retryService.saveFailedNotification(message.getAppointmentId(), message.getNotificationType(), result.getErrorMessage());
            return;
        }

        // Throw so Spring AMQP NACKs the message → DLX → wait queue → back to main queue after 30s
        retryService.saveFailedNotification(message.getAppointmentId(), message.getNotificationType(), result.getErrorMessage());
        throw new AmqpRejectAndDontRequeueException("Delivery failed, requeuing via DLX: " + result.getErrorMessage());
    }

    private long getRetryCount(Map<String, Object> headers) {
        Object xDeath = headers.get("x-death");
        if (!(xDeath instanceof List)) return 0;
        return ((List<?>) xDeath).stream()
                .filter(entry -> entry instanceof Map)
                .mapToLong(entry -> {
                    Object count = ((Map<?, ?>) entry).get("count");
                    return count instanceof Number ? ((Number) count).longValue() : 0L;
                })
                .sum();
    }
}
