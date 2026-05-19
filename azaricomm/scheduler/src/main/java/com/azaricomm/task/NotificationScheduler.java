package com.azaricomm.task;

import com.azaricomm.model.NotificationTask;
import com.azaricomm.repository.NotificationTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    private final NotificationTaskRepository repository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.exchange}")
    private String exchangeName;

    // Spring Boot injecteert de database en RabbitMQ automatisch via deze constructor
    public NotificationScheduler(NotificationTaskRepository repository, RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Deze methode draait elke 60 seconden (60000 milliseconden).
     * Hij zoekt naar taken die de deur uit moeten en stuurt ze naar RabbitMQ.
     */
    @Scheduled(fixedDelay = 60000)
    public void processDueNotifications() {
        log.info("Wekker gaat af: Zoeken naar openstaande notificaties...");

        // 1. Zoek alle taken die PENDING zijn en waarvan de geplande tijd NU of in het verleden is
        List<NotificationTask> dueTasks = repository.findByStatusAndScheduledTimeBefore("PENDING", Instant.now());

        if (dueTasks.isEmpty()) {
            log.info("Geen nieuwe notificaties om te versturen.");
            return;
        }

        log.info("{} openstaande notificatie(s) gevonden. Starten met verwerken.", dueTasks.size());

        // 2. Loop door de taken en stuur ze door
        for (NotificationTask task : dueTasks) {
            try {
                // Update status direct in DB om te voorkomen dat hij de volgende minuut wéér wordt gepakt
                task.setStatus("QUEUED");
                repository.save(task);

                // 3. Stuur het bericht naar de RabbitMQ Exchange (gebruik providerId als routing key)
                rabbitTemplate.convertAndSend(exchangeName, task.getProviderId(), task);

                log.info("Notificatie voor afspraak {} succesvol naar de wachtrij (Provider: {}) gestuurd.",
                        task.getAppointmentId(), task.getProviderId());

            } catch (Exception e) {
                log.error("Fout bij het naar de wachtrij sturen van taak " + task.getId(), e);
                // Bij een crash markeren we hem als FAILED, dan kan een beheerder ernaar kijken
                task.setStatus("FAILED");
                repository.save(task);
            }
        }
    }
}