package org.openmrs.module.azaricomm.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("azaricomm.NotificationService")
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ObjectMapper objectMapper;

    @Autowired
    private RabbitMQService rabbitMQService;

    public NotificationService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    /**
     * Publishes a notification to RabbitMQ for the communication service to pick up.
     *
     * Routing key format: notification.{type}
     * e.g. notification.REMINDER_24H, notification.CANCELLATION
     *
     * @param message the notification to send
     * @return true if published successfully
     */
    public boolean sendNotification(NotificationMessage message) {
        try {
            String routingKey = "notification." + message.getNotificationType();
            String json = objectMapper.writeValueAsString(message);
            rabbitMQService.publish(routingKey, json);
            log.info("Published notification: type={}, patient={}, appointment={}",
                    message.getNotificationType(),
                    message.getPatientUuid(),
                    message.getAppointmentUuid());
            return true;
        } catch (Exception e) {
            log.error("Failed to publish notification for patient {}: {}",
                    message.getPatientUuid(), e.getMessage(), e);
            return false;
        }
    }
}
