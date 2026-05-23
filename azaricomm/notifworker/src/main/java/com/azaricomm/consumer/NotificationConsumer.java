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
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

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

    /**
     * Luistert naar RabbitMQ. Bij een RuntimeException treedt Exponential Backoff op:
     * Poging 1: Direct | Poging 2: Na 2 seconden | Poging 3: Na 4 seconden.
     */
    @RabbitListener(queues = "${rabbitmq.queue:azaricomm.notifications}")
    @Retryable(
            value = { RuntimeException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000, multiplier = 2.0)
    )
    public void handleNotification(NotificationMessage message) {
        log.info("[Consumer] Processing notification: {}", message);

        // 1. Verrijken (Downtime OpenMRS / Database check)
        // Als de enrichment service false returnt óf een crash gooit omdat OpenMRS plat ligt:
        if (!enrichmentService.enrich(message)) {
            throw new RuntimeException("OpenMRS/Database downtime gedetecteerd tijdens verrijken van afspraak: " + message.getAppointmentId());
        }

        // 2. Validatie (Functionele check)
        // Als data corrupt is, heeft herhalen geen zin. Dit gooien we direct weg (discard).
        if (!validationService.validateMessage(message)) {
            log.warn("[Consumer] Validation failed, data corrupt. Discarding message permanently: {}", message);
            return;
        }

        String currentStatus = retryService.getNotificationStatus(message.getAppointmentId(), message.getNotificationType());
        if ("SENT".equals(currentStatus)) {
            log.warn("[Idempotency] Notification already sent, discarding redelivered message for appointmentId={} type={}",
                    message.getAppointmentId(), message.getNotificationType());
            return;
        }

        // 3. Routering & Verzending naar Provider (Downtime Provider check)
        DeliveryResult result = providerRouter.route(message);

        if (result.isSuccess()) {
            log.info("[Consumer] Notification delivered: {}", result);
            retryService.markNotificationAsSent(message.getAppointmentId(), message.getNotificationType());
        } else {
            log.error("[Consumer] Notification delivery failed: {}", result);

            // Sla de fout lokaal op voor het dashboard
            retryService.saveFailedNotification(
                    message.getAppointmentId(),
                    message.getNotificationType(),
                    result.getErrorMessage()
            );

            // GOOI EXCEPTION: Dit triggert de @Retryable backoff!
            throw new RuntimeException("Messaging provider downtime voor " + message.getProvider() + " | Reden: " + result.getErrorMessage());
        }
    }

    /**
     * Dit wordt pas uitgevoerd als ALLE 3 de pogingen van handleNotification() zijn gecrasht.
     * Het bericht wordt nu definitief naar de Dead Letter Queue (DLQ) gestuurd.
     */
    @Recover
    public void handleAllRetriesFailed(RuntimeException e, NotificationMessage message) {
        log.error("[FATAAL - DLQ] Notificatie voor afspraak {} na 3 pogingen definitief mislukt. Reden: {}",
                message.getAppointmentId(), e.getMessage());

        // Vertel RabbitMQ: gooi dit bericht NIET terug in de hoofdqueue (requeue=false),
        // maar stuur hem direct door naar de Dead Letter Exchange (die in de config is ingesteld).
        throw new AmqpRejectAndDontRequeueException("Doorgestuurd naar DLQ wegens aanhoudende downtime", e);
    }
}