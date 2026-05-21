package com.azaricomm.service;

import com.azaricomm.model.Appointment;
import com.azaricomm.model.NotificationMessage;
import com.azaricomm.repository.AppointmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class NotificationValidationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationValidationService.class);

    private final AppointmentRepository appointmentRepository;
    private final MongoTemplate mongoTemplate;

    public NotificationValidationService(AppointmentRepository appointmentRepository,
                                        MongoTemplate mongoTemplate) {
        this.appointmentRepository = appointmentRepository;
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Validates if a notification message should be sent
     * Checks:
     * 1. Appointment has not yet passed
     * 2. This notification type has not already been sent
     */
    public boolean validateMessage(NotificationMessage message) {
        try {
            if (message == null || message.getAppointmentId() == null) {
                log.warn("Invalid message: null or missing appointmentId");
                return false;
            }

            // Check if appointment exists
            Appointment appointment = appointmentRepository.findById(message.getAppointmentId())
                    .orElse(null);

            if (appointment == null) {
                log.warn("Appointment not found: {}", message.getAppointmentId());
                return false;
            }

            // Check if appointment has already passed
            if (isAppointmentPassed(appointment.getScheduledTime())) {
                log.info("Skipping notification for past appointment: {} scheduled at {}",
                        message.getAppointmentId(), appointment.getScheduledTime());
                return false;
            }

            // Check if this notification has already been sent
            if (isNotificationAlreadySent(appointment, message.getNotificationType())) {
                log.info("Skipping duplicate notification for appointment: {} type: {}",
                        message.getAppointmentId(), message.getNotificationType());
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("Error validating notification for appointmentId: {}",
                    message.getAppointmentId(), e);
            return false;
        }
    }

    /**
     * Checks if the appointment date/time has already passed
     */
    private boolean isAppointmentPassed(Instant appointmentTime) {
        if (appointmentTime == null) {
            return false;
        }
        return appointmentTime.isBefore(Instant.now());
    }

    /**
     * Checks if this specific notification type has already been sent
     * Compares with NotificationTimers status for the appointment
     */
    private boolean isNotificationAlreadySent(Appointment appointment, String notificationType) {
        if (appointment.getNotifications() == null) {
            return false;
        }

        if ("REMINDER_24H".equalsIgnoreCase(notificationType)) {
            String status = appointment.getNotifications().getReminder24h();
            return status != null && status.equalsIgnoreCase("SENT");
        } else if ("REMINDER_1H".equalsIgnoreCase(notificationType)) {
            String status = appointment.getNotifications().getReminder1h();
            return status != null && status.equalsIgnoreCase("SENT");
        }

        return false;
    }

    /**
     * Marks a notification as sent by updating the appointment's notification timeline
     */
    public void markNotificationAsSent(String appointmentId, String notificationType) {
        try {
            Query query = new Query(Criteria.where("id").is(appointmentId));
            Update update = new Update();

            if ("REMINDER_24H".equalsIgnoreCase(notificationType)) {
                update.set("notifications.reminder24h", "SENT");
            } else if ("REMINDER_1H".equalsIgnoreCase(notificationType)) {
                update.set("notifications.reminder1h", "SENT");
            }

            mongoTemplate.findAndModify(query, update, Appointment.class);
            log.debug("Marked notification as sent - appointmentId: {} type: {}",
                    appointmentId, notificationType);
        } catch (Exception e) {
            log.error("Error marking notification as sent - appointmentId: {} type: {}",
                    appointmentId, notificationType, e);
            throw new RuntimeException("Failed to mark notification as sent", e);
        }
    }
}
