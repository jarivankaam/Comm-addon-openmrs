package com.azaricomm.task;

import com.azaricomm.model.Appointment;
import com.azaricomm.model.NotificationMessage;
import com.azaricomm.repository.AppointmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final AppointmentRepository repository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange}")
    private String exchangeName;

    public NotificationScheduler(AppointmentRepository repository, RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelay = 60000)
    public void processAppointments() {
        Instant nu = Instant.now();

        // --- STAP 2: 24 UUR VAN TEVOREN ---
        Instant end24h = nu.plus(24, ChronoUnit.HOURS);

        List<Appointment> tasks24h = repository.findTasksFor24hReminder(nu, end24h);
        for (Appointment app : tasks24h) {
            try {
                NotificationMessage payload = buildPayload(app, "REMINDER_24H");
                rabbitTemplate.convertAndSend(exchangeName, "notification.reminder", payload);
                app.getNotifications().setReminder24h("QUEUED");
                repository.save(app);

                log.info("24h Herinnering-ID {} succesvol naar queue gestuurd.", app.getId());
            } catch (Exception e) {
                log.error("Fout bij 24h verwerking voor ID: " + app.getId(), e);
                app.getNotifications().setReminder24h("FAILED");
                repository.save(app);
            }
        }

        // --- STAP 3: 1 UUR VAN TEVOREN ---
        Instant end1h = nu.plus(1, ChronoUnit.HOURS);

        List<Appointment> tasks1h = repository.findTasksFor1hReminder(nu, end1h);
        for (Appointment app : tasks1h) {
            try {
                NotificationMessage payload = buildPayload(app, "REMINDER_1H");
                rabbitTemplate.convertAndSend(exchangeName, "notification.reminder", payload);
                app.getNotifications().setReminder1h("QUEUED");
                repository.save(app);

                log.info("1h Herinnering-ID {} succesvol naar queue gestuurd.", app.getId());
            } catch (Exception e) {
                log.error("Fout bij 1h verwerking voor ID: " + app.getId(), e);
                app.getNotifications().setReminder1h("FAILED");
                repository.save(app);
            }
        }
    }

    private NotificationMessage buildPayload(Appointment app, String notificationType) {
        String provider = app.getProviderId();
        if (provider == null || provider.trim().isEmpty()) {
            provider = "swiftsend";
        }
        String appointmentId = app.getAppointmentId();
        if (appointmentId == null || appointmentId.trim().isEmpty()) {
            appointmentId = app.getId();
        }
        NotificationMessage payload = new NotificationMessage(appointmentId, notificationType, provider);
        if (app.getScheduledTime() != null) {
            payload.setAppointmentDateTime(app.getScheduledTime().toString());
        }
        return payload;
    }
}
