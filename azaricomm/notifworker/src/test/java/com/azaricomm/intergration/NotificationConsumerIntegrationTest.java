package com.azaricomm.intergration;

import com.azaricomm.model.Appointment;
import com.azaricomm.model.DeliveryResult;
import com.azaricomm.model.NotificationMessage;
import com.azaricomm.model.NotificationTimeline; // <-- Deze import is toegevoegd
import com.azaricomm.provider.ProviderRouter;
import com.azaricomm.service.AppointmentEnrichmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class NotificationConsumerIntegrationTest {

    @Container
    static MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:6.0"));

    @Container
    static RabbitMQContainer rabbitMQContainer = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.11-management"));

    static {
        mongoDBContainer.start();
        rabbitMQContainer.start();
        System.setProperty("spring.data.mongodb.uri", mongoDBContainer.getReplicaSetUrl());
        System.setProperty("spring.rabbitmq.host", rabbitMQContainer.getHost());
        System.setProperty("spring.rabbitmq.port", String.valueOf(rabbitMQContainer.getAmqpPort()));
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MongoTemplate mongoTemplate;

    @MockBean
    private AppointmentEnrichmentService enrichmentService;

    @MockBean
    private ProviderRouter providerRouter;

    @BeforeEach
    void setUp() {
        mongoTemplate.getCollection("appointments").drop();
    }

    @Test
    void testFullNotificationFlow_Success() {
        // Arrange
        Appointment appointment = new Appointment();
        appointment.setId("6a1054a33c20b15d40b54ff7");
        appointment.setNotifications(new NotificationTimeline()); // <-- Aangepast naar het juiste type

        mongoTemplate.save(appointment);

        NotificationMessage message = new NotificationMessage();
        message.setAppointmentId(appointment.getId());
        message.setNotificationType("REMINDER_1H");
        message.setProvider("swiftsend");

        when(enrichmentService.enrich(any())).thenReturn(true);
        when(providerRouter.route(any())).thenReturn(DeliveryResult.success("swiftsend", "MSG-100"));

        // Act
        rabbitTemplate.convertAndSend("azaricomm.events", "notification.queue", message);

        // Assert
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Map doc = mongoTemplate.findOne(new Query(), Map.class, "appointments");
            assertNotNull(doc);

            Map notifications = (Map) doc.get("notifications");
            assertNotNull(notifications);
            assertEquals("SENT", notifications.get("reminder1h"));
        });
    }

    @Test
    void testFullNotificationFlow_ProviderFailure_TriggersRetryStorage() {
        // Arrange
        Appointment appointment = new Appointment();
        appointment.setId("6a1056ef3c20b15d40b54ff9"); // Zorg dat deze ID uniek is per test of correct wordt opgeruimd
        appointment.setNotifications(new NotificationTimeline()); // <-- Aangepast naar het juiste type

        mongoTemplate.save(appointment);

        NotificationMessage message = new NotificationMessage();
        message.setAppointmentId(appointment.getId());
        message.setNotificationType("REMINDER_1H");
        message.setProvider("swiftsend");

        when(enrichmentService.enrich(any())).thenReturn(true);
        when(providerRouter.route(any())).thenReturn(DeliveryResult.failure("swiftsend", "TIMEOUT_ERROR"));

        // Act
        rabbitTemplate.convertAndSend("azaricomm.events", "notification.queue", message);

        // Assert
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            Map doc = mongoTemplate.findOne(new Query(), Map.class, "appointments");
            assertNotNull(doc);

            Map notifications = (Map) doc.get("notifications");
            assertNotNull(notifications);
            assertEquals("FAILED", notifications.get("reminder1h"));
            assertEquals(1, notifications.get("reminder1hRetryCount"));
            assertEquals("TIMEOUT_ERROR", notifications.get("reminder1hLastError"));
            assertNotNull(notifications.get("reminder1hNextRetryTime"));
        });
    }
}