package com.azaricomm.consumer;

import com.azaricomm.metrics.NotifWorkerMetrics;
import com.azaricomm.model.DeliveryResult;
import com.azaricomm.model.NotificationMessage;
import com.azaricomm.provider.ProviderRouter;
import com.azaricomm.service.AppointmentEnrichmentService;
import com.azaricomm.service.NotificationRetryService;
import com.azaricomm.service.NotificationValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final ProviderRouter providerRouter;
    private final NotificationValidationService validationService;
    private final NotificationRetryService retryService;
    private final AppointmentEnrichmentService enrichmentService;
    private final NotifWorkerMetrics notifWorkerMetrics;

    public NotificationConsumer(ProviderRouter providerRouter,
                               NotificationValidationService validationService,
                               NotificationRetryService retryService,
                               AppointmentEnrichmentService enrichmentService,
                               NotifWorkerMetrics notifWorkerMetrics) {
        this.providerRouter = providerRouter;
        this.validationService = validationService;
        this.retryService = retryService;
        this.enrichmentService = enrichmentService;
        this.notifWorkerMetrics = notifWorkerMetrics;
    }

    @RabbitListener(queues = "${rabbitmq.queue:azaricomm.notifications}")
    public void handleNotification(NotificationMessage message) {
        log.info("Received notification: {}", message);
        notifWorkerMetrics.recordNotificationReceived();

        try {
            if (!enrichmentService.enrich(message)) {
                log.warn("Could not enrich notification, discarding message for appointment: {}", message.getAppointmentId());
                notifWorkerMetrics.recordNotificationDiscarded("enrichment_failed");
                return;
            }

            if (!validationService.validateMessage(message)) {
                log.warn("Notification validation failed, discarding message: {}", message);
                notifWorkerMetrics.recordNotificationDiscarded("validation_failed");
                return;
            }

            String currentStatus = retryService.getNotificationStatus(message.getAppointmentId(), message.getNotificationType());
            if ("SENT".equals(currentStatus)) {
                log.warn("[Idempotency] Notification already sent, discarding redelivered message for appointmentId={} type={}",
                        message.getAppointmentId(), message.getNotificationType());
                notifWorkerMetrics.recordNotificationDiscarded("idempotent");
                return;
            }

            DeliveryResult result = providerRouter.route(message);

            if (result.isSuccess()) {
                log.info("Notification delivered: {}", result);
                retryService.markNotificationAsSent(message.getAppointmentId(), message.getNotificationType());
                notifWorkerMetrics.recordNotificationDelivered(message.getProvider());
            } else {
                log.error("Notification delivery failed: {}", result);
                retryService.saveFailedNotification(
                    message.getAppointmentId(),
                    message.getNotificationType(),
                    result.getErrorMessage()
                );
                notifWorkerMetrics.recordNotificationFailed(message.getProvider());
            }

        } catch (Exception e) {
            log.error("Failed to process notification for appointment: {} - {}", message.getAppointmentId(), e.getMessage(), e);
        }
    }
}
