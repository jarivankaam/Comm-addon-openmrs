package com.azaricomm;

import com.azaricomm.model.NotificationTask;
import com.azaricomm.repository.NotificationTaskRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Instant;

@SpringBootApplication
@EnableScheduling
public class SchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedulerApplication.class, args);
    }

    /**
     * Dit blokje code voert automatisch uit zodra de applicatie opstart.
     * Het plaatst één verlopen taak in MongoDB conform het nieuwe datamodel!
     */
    @Bean
    public CommandLineRunner deSeeder(NotificationTaskRepository repository) {
        return args -> {
            // Maak de database eerst even leeg voor een schone test
            repository.deleteAll();

            NotificationTask testTaak = new NotificationTask();
            testTaak.setAppointmentId("openmrs-appt-9999");

            // We zetten de wektijd op 10 minuten geleden, zodat de scheduler hem direct MOET pakken
            testTaak.setScheduledTime(Instant.now().minusSeconds(600));
            testTaak.setStatus("PENDING");
            testTaak.setProviderId("twilioprovider"); // Dit wordt de routing-key in RabbitMQ

            // Conform je diagram: Gevoelige PII data verhuist naar het gecodeerde/versleutelde blok.
            // Voor deze test zetten we er een gewone JSON-string in (straks wordt dit écht AES-versleuteld).
            String gesimuleerdeEncryptie = "{"
                    + "\"patientId\":\"patient-azari-01\","
                    + "\"phoneNumber\":\"+31612345678\","
                    + "\"location\":\"Kliniek A, Kamer 4\","
                    + "\"instructions\":\"Nuchter meenemen\""
                    + "}";

            testTaak.setDataEncrypted(gesimuleerdeEncryptie);

            repository.save(testTaak);
            System.out.println(">>> SUCCES: Test-notificatie (Nieuw Model) in MongoDB geplaatst! <<<");
        };
    }
}