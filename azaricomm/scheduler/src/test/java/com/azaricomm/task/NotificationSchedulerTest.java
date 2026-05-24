package com.azaricomm.task;

import com.azaricomm.metrics.SchedulerMetrics;
import com.azaricomm.model.Appointment;
import com.azaricomm.model.NotificationMessage;
import com.azaricomm.repository.AppointmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

    @Mock
    private AppointmentRepository repository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private SchedulerMetrics schedulerMetrics;

    @InjectMocks
    private NotificationScheduler scheduler;

    private static final String EXCHANGE_NAME = "test-exchange";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "exchangeName", EXCHANGE_NAME);
    }

    @Test
    void when24hReminderFound_ShouldSendMinimalPayloadAndSetQueued() {
        Appointment app = new Appointment();
        app.setId("64f1a2b3c4d5e6f7a8b9c0d1");

        when(repository.findTasksFor24hReminder(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(app));
        when(repository.findTasksFor1hReminder(any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        scheduler.processAppointments();

        ArgumentCaptor<NotificationMessage> messageCaptor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(EXCHANGE_NAME),
                eq("notification.reminder"),
                messageCaptor.capture()
        );

        NotificationMessage payload = messageCaptor.getValue();
        assertEquals("64f1a2b3c4d5e6f7a8b9c0d1", payload.getAppointmentId());
        assertEquals("REMINDER_24H", payload.getNotificationType());

        verify(schedulerMetrics, times(1)).recordNotificationQueued("REMINDER_24H");
        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);

        verify(mongoTemplate, times(1)).updateFirst(
                queryCaptor.capture(),
                updateCaptor.capture(),
                eq("appointments")
        );

        assertEquals("64f1a2b3c4d5e6f7a8b9c0d1", queryCaptor.getValue().getQueryObject().get("_id"));
        Object setUpdate = updateCaptor.getValue().getUpdateObject().get("$set");
        assertTrue(setUpdate instanceof Map);
        assertEquals("QUEUED", ((Map<?, ?>) setUpdate).get("notifications.reminder24h"));
    }

    @Test
    void when1hReminderFound_ShouldSendMinimalPayloadAndSetQueued() {
        Appointment app = new Appointment();
        app.setId("64f1a2b3c4d5e6f7a8b9c0d2");

        when(repository.findTasksFor24hReminder(any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(repository.findTasksFor1hReminder(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(app));

        scheduler.processAppointments();

        ArgumentCaptor<NotificationMessage> messageCaptor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(rabbitTemplate, times(1)).convertAndSend(any(), any(), messageCaptor.capture());

        NotificationMessage payload = messageCaptor.getValue();
        assertEquals("64f1a2b3c4d5e6f7a8b9c0d2", payload.getAppointmentId());
        assertEquals("REMINDER_1H", payload.getNotificationType());

        verify(schedulerMetrics, times(1)).recordNotificationQueued("REMINDER_1H");
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(1)).updateFirst(any(Query.class), updateCaptor.capture(), eq("appointments"));

        Object setUpdate = updateCaptor.getValue().getUpdateObject().get("$set");
        assertEquals("QUEUED", ((Map<?, ?>) setUpdate).get("notifications.reminder1h"));
    }

    @Test
    void whenRabbitThrowsException_ShouldSetStatusToFailedAndRecordMetrics() {
        // Arrange
        Appointment app = new Appointment();
        app.setId("64f1a2b3c4d5e6f7a8b9c0d3");

        when(repository.findTasksFor24hReminder(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(app));
        when(repository.findTasksFor1hReminder(any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        doThrow(new RuntimeException("RabbitMQ Connection Lost")).when(rabbitTemplate)
                .convertAndSend(any(String.class), any(String.class), any(NotificationMessage.class));

        // Act
        scheduler.processAppointments();

        // Assert
        verify(schedulerMetrics, times(1)).recordNotificationFailed("REMINDER_24H");
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(1)).updateFirst(any(Query.class), updateCaptor.capture(), eq("appointments"));

        Object setUpdate = updateCaptor.getValue().getUpdateObject().get("$set");
        assertEquals("FAILED", ((Map<?, ?>) setUpdate).get("notifications.reminder24h"));
    }
}