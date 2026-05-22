package com.azaricomm.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NotificationRetryService {

    private static final Logger log = LoggerFactory.getLogger(NotificationRetryService.class);

    private final MongoTemplate mongoTemplate;

    public NotificationRetryService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public String getNotificationStatus(String appointmentId, String notificationType) {
        try {
            Query query = new Query(Criteria.where("_id").is(appointmentId));
            Map doc = mongoTemplate.findOne(query, Map.class, "appointments");
            if (doc == null) return null;
            Map<?, ?> notifications = (Map<?, ?>) doc.get("notifications");
            if (notifications == null) return null;
            String field = "REMINDER_24H".equalsIgnoreCase(notificationType) ? "reminder24h" : "reminder1h";
            return (String) notifications.get(field);
        } catch (Exception e) {
            log.error("Failed to get notification status - appointmentId: {} type: {}", appointmentId, notificationType, e);
            return null;
        }
    }

    public void markNotificationAsSent(String appointmentId, String notificationType) {
        try {
            Query query = new Query(Criteria.where("_id").is(appointmentId));
            Update update;

            if ("REMINDER_24H".equalsIgnoreCase(notificationType)) {
                update = new Update()
                        .set("notifications.reminder24h", "SENT")
                        .unset("notifications.reminder24hLastError");
            } else {
                update = new Update()
                        .set("notifications.reminder1h", "SENT")
                        .unset("notifications.reminder1hLastError");
            }

            mongoTemplate.findAndModify(query, update, Map.class, "appointments");
            log.debug("Marked notification as sent - appointmentId: {} type: {}", appointmentId, notificationType);
        } catch (Exception e) {
            log.error("Failed to mark notification as sent - appointmentId: {} type: {}", appointmentId, notificationType, e);
        }
    }

    public void saveFailedNotification(String appointmentId, String notificationType, String errorMessage) {
        try {
            Query query = new Query(Criteria.where("_id").is(appointmentId));
            Update update;

            if ("REMINDER_24H".equalsIgnoreCase(notificationType)) {
                update = new Update()
                        .set("notifications.reminder24h", "FAILED")
                        .set("notifications.reminder24hLastError", errorMessage)
                        .inc("notifications.reminder24hRetryCount", 1);
            } else {
                update = new Update()
                        .set("notifications.reminder1h", "FAILED")
                        .set("notifications.reminder1hLastError", errorMessage)
                        .inc("notifications.reminder1hRetryCount", 1);
            }

            mongoTemplate.findAndModify(query, update, Map.class, "appointments");
            log.info("Saved failed notification - appointmentId: {} type: {}", appointmentId, notificationType);
        } catch (Exception e) {
            log.error("Failed to save failed notification - appointmentId: {} type: {}", appointmentId, notificationType, e);
        }
    }
}
