package com.azaricomm.task;

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
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

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

    @InjectMocks
    private NotificationScheduler scheduler;

    private static final String EXCHANGE_NAME = "test-exchange";

    private final Instant scheduledTime = Instant.now().plus(1, ChronoUnit.DAYS);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "exchangeName", EXCHANGE_NAME);
    }

    @Test
    void when24hReminderFound_ShouldSendToRabbitAndSetStatusToQueued() {
        Appointment app = new Appointment();
        app.setId("mongo-id-123");
        app.setAppointmentId("openmrs-id-123");
        app.setProviderId("legacylink");
        app.setScheduledTime(scheduledTime);

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
        assertEquals("openmrs-id-123", payload.getAppointmentId());
        assertEquals("REMINDER_24H", payload.getNotificationType());
        assertEquals("legacylink", payload.getProvider());
        assertEquals(scheduledTime.toString(), payload.getAppointmentDateTime());

        ArgumentCaptor<Query> queryCaptor = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);

        verify(mongoTemplate, times(1)).updateFirst(
                queryCaptor.capture(),
                updateCaptor.capture(),
                eq("appointments")
        );

        assertEquals("mongo-id-123", queryCaptor.getValue().getQueryObject().get("_id"));

        Object setUpdate = updateCaptor.getValue().getUpdateObject().get("$set");
        assertTrue(setUpdate instanceof java.util.Map);
        assertEquals("QUEUED", ((java.util.Map<?, ?>) setUpdate).get("notifications.reminder24h"));
    }

    @Test
    void when1hReminderFound_ShouldSendToRabbitAndSetStatusToQueued() {
        Appointment app = new Appointment();
        app.setId("mongo-id-456");
        app.setAppointmentId("openmrs-id-456");
        app.setProviderId("asyncflow");
        app.setScheduledTime(scheduledTime);

        when(repository.findTasksFor24hReminder(any(Instant.class), any(Instant.class)))
                .thenReturn(Collections.emptyList());
        when(repository.findTasksFor1hReminder(any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(app));

        scheduler.processAppointments();

        ArgumentCaptor<NotificationMessage> messageCaptor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(EXCHANGE_NAME),
                eq("notification.reminder"),
                messageCaptor.capture()
        );

        NotificationMessage payload = messageCaptor.getValue();
        assertEquals("openmrs-id-456", payload.getAppointmentId());
        assertEquals("REMINDER_1H", payload.getNotificationType());
        assertEquals("asyncflow", payload.getProvider());

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(1)).updateFirst(any(Query.class), updateCaptor.capture(), eq("appointments"));

        // FIX: Object-gebaseerde assertie
        Object setUpdate = updateCaptor.getValue().getUpdateObject().get("$set");
        assertTrue(setUpdate instanceof java.util.Map);
        assertEquals("QUEUED", ((java.util.Map<?, ?>) setUpdate).get("notifications.reminder1h"));
    }

    @Test
    void whenRabbitThrowsExceptionDuring24h_ShouldSetStatusToFailedAndContinue() {
        Appointment app = new Appointment();
        app.setId("mongo-id-fail");

        when(repository.findTasksFor24hReminder(any(Instant.class), any(Instant.class))).thenReturn(List.of(app));
        when(repository.findTasksFor1hReminder(any(Instant.class), any(Instant.class))).thenReturn(Collections.emptyList());

        doThrow(new RuntimeException("Rabbit down")).when(rabbitTemplate)
                .convertAndSend(any(String.class), any(String.class), any(NotificationMessage.class));

        scheduler.processAppointments();

        ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, times(1)).updateFirst(any(Query.class), updateCaptor.capture(), eq("appointments"));

        Object setUpdate = updateCaptor.getValue().getUpdateObject().get("$set");
        assertTrue(setUpdate instanceof java.util.Map);
        assertEquals("FAILED", ((java.util.Map<?, ?>) setUpdate).get("notifications.reminder24h"));
    }

    @Test
    void whenAppointmentFieldsAreMissing_ShouldFallbackToDefaultsInPayload() {
        Appointment app = new Appointment();
        app.setId("mongo-id-789");
        app.setAppointmentId(null);
        app.setProviderId("   ");

        when(repository.findTasksFor24hReminder(any(Instant.class), any(Instant.class))).thenReturn(List.of(app));
        when(repository.findTasksFor1hReminder(any(Instant.class), any(Instant.class))).thenReturn(Collections.emptyList());

        scheduler.processAppointments();

        ArgumentCaptor<NotificationMessage> messageCaptor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(rabbitTemplate, times(1)).convertAndSend(any(), any(), messageCaptor.capture());

        NotificationMessage payload = messageCaptor.getValue();
        assertEquals("mongo-id-789", payload.getAppointmentId());
        assertEquals("swiftsend", payload.getProvider());
    }
}