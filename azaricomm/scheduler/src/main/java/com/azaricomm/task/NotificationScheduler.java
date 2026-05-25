package com.azaricomm.task;

import com.azaricomm.metrics.SchedulerMetrics;
import com.azaricomm.model.Appointment;
import com.azaricomm.model.NotificationMessage;
import com.azaricomm.repository.AppointmentRepository;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private static final TextMapGetter<Map<String, String>> MAP_GETTER = new TextMapGetter<>() {
        @Override public Iterable<String> keys(Map<String, String> carrier) { return carrier.keySet(); }
        @Override public String get(Map<String, String> carrier, String key) { return carrier.get(key); }
    };

    private final AppointmentRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final MongoTemplate mongoTemplate;
    private final SchedulerMetrics schedulerMetrics;
    private final Tracer tracer;

    @Value("${app.rabbitmq.exchange}")
    private String exchangeName;

    public NotificationScheduler(AppointmentRepository repository, RabbitTemplate rabbitTemplate,
                                 MongoTemplate mongoTemplate, SchedulerMetrics schedulerMetrics) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.mongoTemplate = mongoTemplate;
        this.schedulerMetrics = schedulerMetrics;
        this.tracer = GlobalOpenTelemetry.getTracer("azaricomm-scheduler");
    }

    @Scheduled(fixedDelay = 60000)
    public void processAppointments() {
        Instant now = Instant.now();

        Instant start24h = now.plus(2, ChronoUnit.HOURS);
        Instant end24h = now.plus(24, ChronoUnit.HOURS);

        // --- STAP 2: 24 UUR VAN TEVOREN ---
        List<Appointment> tasks24h = repository.findTasksFor24hReminder(start24h, end24h);
        for (Appointment app : tasks24h) {
            publishNotification(app, "REMINDER_24H", "notifications.reminder24h");
        }

        // --- STAP 3: 1 UUR VAN TEVOREN ---
        Instant end1h = now.plus(1, ChronoUnit.HOURS);
        List<Appointment> tasks1h = repository.findTasksFor1hReminder(now, end1h);
        for (Appointment app : tasks1h) {
            publishNotification(app, "REMINDER_1H", "notifications.reminder1h");
        }
    }

    private void publishNotification(Appointment app, String type, String statusField) {
        Context parentContext = extractContext(app.getTraceContext());
        Span span = tracer.spanBuilder("scheduler.queue-notification")
                .setParent(parentContext)
                .setSpanKind(SpanKind.PRODUCER)
                .setAttribute("appointment.id", app.getId() != null ? app.getId() : "")
                .setAttribute("notification.type", type)
                .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            NotificationMessage payload = buildPayload(app, type);
            rabbitTemplate.convertAndSend(exchangeName, "notification.reminder", payload);
            setNotificationStatus(app.getId(), statusField, "QUEUED");
            schedulerMetrics.recordNotificationQueued(type);
            log.info("{} herinnering voor ID {} naar queue gestuurd.", type, app.getId());
        } catch (Exception e) {
            span.recordException(e);
            setNotificationStatus(app.getId(), statusField, "FAILED");
            schedulerMetrics.recordNotificationFailed(type);
            log.error("Fout bij {} verwerking voor ID: {}", type, app.getId(), e);
        } finally {
            span.end();
        }
    }

    private Context extractContext(Map<String, String> traceContext) {
        if (traceContext == null || traceContext.isEmpty()) {
            return Context.current();
        }
        return W3CTraceContextPropagator.getInstance().extract(Context.current(), traceContext, MAP_GETTER);
    }

    private void setNotificationStatus(String appointmentId, String field, String status) {
        Query query = new Query(Criteria.where("_id").is(appointmentId));
        Update update = new Update().set(field, status);
        mongoTemplate.updateFirst(query, update, "appointments");
    }

    private NotificationMessage buildPayload(Appointment app, String notificationType) {
        // Directe en vederlichte mapping: MongoDB id wordt de appointmentId op RabbitMQ
        return new NotificationMessage(app.getId(), notificationType);
    }
}