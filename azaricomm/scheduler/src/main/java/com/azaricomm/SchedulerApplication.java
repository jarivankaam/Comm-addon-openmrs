package com.azaricomm;

import com.azaricomm.model.Appointment;
import com.azaricomm.repository.AppointmentRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@SpringBootApplication
@EnableScheduling
public class SchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedulerApplication.class, args);
    }

    /**
     * Dit blokje code voert automatisch uit zodra de applicatie opstart.
     * Het plaatst twee testafspraken in MongoDB om de vensters van je scheduler te testen!
     */
    @Bean
    public CommandLineRunner deSeeder(AppointmentRepository repository) {
        return args -> {
            // Maak de database eerst even leeg voor een schone test
            repository.deleteAll();

            Instant nu = Instant.now();

            // --- TEST AFSPRAAK 1: Moet de 24-uurs herinnering triggeren ---
            Appointment afspraak24h = new Appointment();
            afspraak24h.setAppointmentId("openmrs-appt-24u-later");
            afspraak24h.setStatus("SCHEDULED");
            // We zetten de afspraaktijd op exact 24 uur vanaf nu minus 2 minuten (valt perfect in het 23h55m-24h venster)
            afspraak24h.setScheduledTime(nu.plus(24, ChronoUnit.HOURS).minus(2, ChronoUnit.MINUTES));

            // De statussen staan standaard op SCHEDULED via de constructor, maar we zetten ze er voor de helderheid bij:
            afspraak24h.getNotifications().setReminder24h("SCHEDULED");
            afspraak24h.getNotifications().setReminder1h("SCHEDULED");

            repository.save(afspraak24h);


            // --- TEST AFSPRAAK 2: Moet de 1-uurs herinnering triggeren ---
            Appointment afspraak1h = new Appointment();
            afspraak1h.setAppointmentId("openmrs-appt-1u-later");
            afspraak1h.setStatus("SCHEDULED");
            // We zetten de afspraaktijd op exact 1 uur vanaf nu minus 2 minuten (valt perfect in het 55m-1h venster)
            afspraak1h.setScheduledTime(nu.plus(1, ChronoUnit.HOURS).minus(2, ChronoUnit.MINUTES));

            afspraak1h.getNotifications().setReminder24h("SCHEDULED");
            afspraak1h.getNotifications().setReminder1h("SCHEDULED");

            repository.save(afspraak1h);

            System.out.println(">>> SUCCES: 2 Nieuwe test-afspraken (24h en 1h) in MongoDB geplaatst! <<<");
        };
    }
}