package com.azaricomm.task;

import com.azaricomm.model.Appointment;
import com.azaricomm.model.NotificationMessage;
import com.azaricomm.model.NotificationTimers;
import com.azaricomm.repository.AppointmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
class NotificationSchedulerIntegrationTest {

    @Autowired
    private NotificationScheduler scheduler;

    @Autowired
    private AppointmentRepository repository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void cleanDatabase() {
        mongoTemplate.dropCollection(Appointment.class);
    }

    @Test
    void testIntegration_24hAnd1hScenarios_WithCorrectTimeWindows() {
        Instant nu = Instant.now();

        // 1. Make an appointment over exactly 10 hours (Must be in the 24 hour loop)
        Appointment app24h = new Appointment();
        app24h.setId("INTEGRATION-ID-24H");
        app24h.setScheduledTime(nu.plus(10, ChronoUnit.HOURS));
        app24h.setStatus("SCHEDULED");
        NotificationTimers timers24h = new NotificationTimers();
        timers24h.setReminder24h("SCHEDULED");
        timers24h.setReminder1h("SCHEDULED");
        app24h.setNotifications(timers24h);

        // 2. Make an appointment over exactly 30 minutes (Must only be in the 1 hour loop)
        Appointment app1h = new Appointment();
        app1h.setId("INTEGRATION-ID-1H");
        app1h.setScheduledTime(nu.plus(30, ChronoUnit.MINUTES));
        app1h.setStatus("SCHEDULED");
        NotificationTimers timers1h = new NotificationTimers();
        timers1h.setReminder24h("SCHEDULED");
        timers1h.setReminder1h("SCHEDULED");
        app1h.setNotifications(timers1h);

        repository.saveAll(List.of(app24h, app1h));

        scheduler.processAppointments();

        ArgumentCaptor<NotificationMessage> messageCaptor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(rabbitTemplate, times(2)).convertAndSend(
                any(),
                eq("notification.reminder"),
                messageCaptor.capture()
        );

        List<NotificationMessage> sentPayloads = messageCaptor.getAllValues();

        assertEquals("INTEGRATION-ID-24H", sentPayloads.get(0).getAppointmentId());
        assertEquals("REMINDER_24H", sentPayloads.get(0).getNotificationType());

        assertEquals("INTEGRATION-ID-1H", sentPayloads.get(1).getAppointmentId());
        assertEquals("REMINDER_1H", sentPayloads.get(1).getNotificationType());

        Appointment updatedApp24h = repository.findById("INTEGRATION-ID-24H").orElseThrow();
        assertEquals("QUEUED", updatedApp24h.getNotifications().getReminder24h());
        assertEquals("SCHEDULED", updatedApp24h.getNotifications().getReminder1h());

        Appointment updatedApp1h = repository.findById("INTEGRATION-ID-1H").orElseThrow();
        assertEquals("SCHEDULED", updatedApp1h.getNotifications().getReminder24h());
        assertEquals("QUEUED", updatedApp1h.getNotifications().getReminder1h());
    }
}