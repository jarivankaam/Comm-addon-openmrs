package com.azaricomm.service;

import com.azaricomm.model.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class NotificationRetryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetryService.class);

    private final MongoTemplate mongoTemplate;
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange:azaricomm.events}")
    private String exchangeName;

    public NotificationRetryService(MongoTemplate mongoTemplate, RabbitTemplate rabbitTemplate) {
        this.mongoTemplate = mongoTemplate;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Save a failed notification for retry or increment retry count if already failed
     */
    public void saveFailedNotification(String appointmentId, String notificationType, String errorMessage) {
        try {
            // Get current retry count
            Query query = new Query(Criteria.where("_id").is(appointmentId));
            Map<String, Object> doc = mongoTemplate.findOne(query, Map.class, "appointments");

            int retryCount = 0;
            if (doc != null && doc.get("notifications") instanceof Map) {
                Map<String, Object> notifs = (Map<String, Object>) doc.get("notifications");
                String retryField = "REMINDER_24H".equalsIgnoreCase(notificationType)
                    ? "reminder24hRetryCount"
                    : "reminder1hRetryCount";
                retryCount = notifs.get(retryField) instanceof Number
                    ? ((Number) notifs.get(retryField)).intValue()
                    : 0;
            }

            // Calculate next retry time
            long delayMillis;
            switch (retryCount) {
                case 0:
                    delayMillis = 10_000; // 10 seconds
                    break;
                case 1:
                    delayMillis = 60_000; // 1 minute
                    break;
                case 2:
                    delayMillis = 3_600_000; // 1 hour
                    break;
                default:
                    log.info("Max retries reached for appointmentId: {} type: {}", appointmentId, notificationType);
                    return;
            }

            Instant nextRetryTime = Instant.now().plusMillis(delayMillis);

            // Update appointment with retry info
            Update update;
            if ("REMINDER_24H".equalsIgnoreCase(notificationType)) {
                update = new Update()
                    .set("notifications.reminder24h", "FAILED")
                    .set("notifications.reminder24hRetryCount", retryCount + 1)
                    .set("notifications.reminder24hNextRetryTime", nextRetryTime)
                    .set("notifications.reminder24hLastError", errorMessage);
            } else {
                update = new Update()
                    .set("notifications.reminder1h", "FAILED")
                    .set("notifications.reminder1hRetryCount", retryCount + 1)
                    .set("notifications.reminder1hNextRetryTime", nextRetryTime)
                    .set("notifications.reminder1hLastError", errorMessage);
            }

            mongoTemplate.findAndModify(query, update, Map.class, "appointments");
            log.info("Saved notification for retry - appointmentId: {} type: {} attempt: {} nextRetry: {}",
                    appointmentId, notificationType, retryCount + 1, nextRetryTime);
        } catch (Exception e) {
            log.error("Failed to save notification for retry - appointmentId: {} type: {}",
                    appointmentId, notificationType, e);
        }
    }

    /**
     * Mark notification as sent (clears retry fields)
     */
    public void markNotificationAsSent(String appointmentId, String notificationType) {
        try {
            Query query = new Query(Criteria.where("_id").is(appointmentId));
            Update update;

            if ("REMINDER_24H".equalsIgnoreCase(notificationType)) {
                update = new Update()
                    .set("notifications.reminder24h", "SENT")
                    .set("notifications.reminder24hRetryCount", 0)
                    .unset("notifications.reminder24hNextRetryTime")
                    .unset("notifications.reminder24hLastError");
            } else {
                update = new Update()
                    .set("notifications.reminder1h", "SENT")
                    .set("notifications.reminder1hRetryCount", 0)
                    .unset("notifications.reminder1hNextRetryTime")
                    .unset("notifications.reminder1hLastError");
            }

            mongoTemplate.findAndModify(query, update, Map.class, "appointments");
            log.debug("Marked notification as sent - appointmentId: {} type: {}", appointmentId, notificationType);
        } catch (Exception e) {
            log.error("Failed to mark notification as sent - appointmentId: {} type: {}",
                    appointmentId, notificationType, e);
        }
    }

    /**
     * Scheduled task: run every 30 seconds to find and retry failed notifications
     */
    @Scheduled(fixedDelay = 30000)
    public void retryFailedNotifications() {
        try {
            Instant now = Instant.now();

            // Find 24h reminders due for retry
            Query query24h = new Query(Criteria
                .where("notifications.reminder24h").is("FAILED")
                .and("notifications.reminder24hNextRetryTime").lte(now)
                .and("notifications.reminder24hRetryCount").lt(3));
            List<Map> due24h = mongoTemplate.find(query24h, Map.class, "appointments");

            for (Map apt : due24h) {
                try {
                    String appointmentId = (String) apt.get("_id");
                    NotificationMessage message = buildNotificationMessage(apt, "REMINDER_24H");
                    rabbitTemplate.convertAndSend(exchangeName, "notification.retry", message);
                    log.info("Republished 24h reminder for retry - appointmentId: {}", appointmentId);
                } catch (Exception e) {
                    log.error("Failed to republish 24h reminder", e);
                }
            }

            // Find 1h reminders due for retry
            Query query1h = new Query(Criteria
                .where("notifications.reminder1h").is("FAILED")
                .and("notifications.reminder1hNextRetryTime").lte(now)
                .and("notifications.reminder1hRetryCount").lt(3));
            List<Map> due1h = mongoTemplate.find(query1h, Map.class, "appointments");

            for (Map apt : due1h) {
                try {
                    String appointmentId = (String) apt.get("_id");
                    NotificationMessage message = buildNotificationMessage(apt, "REMINDER_1H");
                    rabbitTemplate.convertAndSend(exchangeName, "notification.retry", message);
                    log.info("Republished 1h reminder for retry - appointmentId: {}", appointmentId);
                } catch (Exception e) {
                    log.error("Failed to republish 1h reminder", e);
                }
            }

            if (due24h.isEmpty() && due1h.isEmpty()) {
                log.debug("No failed notifications due for retry");
            }
        } catch (Exception e) {
            log.error("Error in retry scheduler", e);
        }
    }

    private NotificationMessage buildNotificationMessage(Map apt, String notificationType) {
        NotificationMessage msg = new NotificationMessage();
        msg.setAppointmentId((String) apt.getOrDefault("appointmentId", apt.get("_id")));
        msg.setNotificationType(notificationType);
        msg.setProvider((String) apt.getOrDefault("providerId", "swiftsend"));
        Object scheduledTime = apt.get("scheduledTime");
        if (scheduledTime != null) {
            msg.setAppointmentDateTime(scheduledTime.toString());
        }
        return msg;
    }
}
