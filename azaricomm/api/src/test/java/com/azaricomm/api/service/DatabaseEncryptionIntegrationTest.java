package com.azaricomm.api.service;

import com.azaricomm.api.model.Appointment;
import com.azaricomm.api.model.AppointmentStatus;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "OPENMRS_BASE_URL=http://localhost:8080",
        "OPENMRS_USERNAME=test",
        "OPENMRS_PASSWORD=test",
        "WEBHOOK_SECRET=test-secret-12345678901234567890123456789012",
        "CRYPTO_SECRET_KEY=DitIsMijnSuperGeheimeSleutel123!"
})
class DatabaseEncryptionIntegrationTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    void whenSavingAppointment_ShouldEncryptSensitiveDataInDatabase() {
        String testId = "test-crypto-123";
        String plainPhone = "+31612345678";

        Appointment appointment = new Appointment();
        appointment.setId(testId);
        appointment.setPatientPhone(plainPhone);
        appointment.setLocation("Gezondheidscentrum Breda");
        appointment.setStatus(AppointmentStatus.SCHEDULED);
        appointment.setScheduledTime(Instant.now().plus(1, ChronoUnit.DAYS));

        mongoTemplate.save(appointment);

        Query query = new Query(Criteria.where("_id").is(testId));
        Document rawDocument = mongoTemplate.findOne(query, Document.class, "appointments");

        assertNotNull(rawDocument, "Het document kon niet worden gevonden in de collectie 'appointments'.");


        assertNull(rawDocument.getString("patientPhone"),
                "Fout: 'patientPhone' staat als los veld in de database (hoort @Transient te zijn).");
        assertNull(rawDocument.getString("location"),
                "Fout: 'location' staat als los veld in de database (hoort @Transient te zijn).");

        String dataEncrypted = rawDocument.getString("dataEncrypted");
        String locationEncrypted = rawDocument.getString("locationEncrypted");

        assertNotNull(dataEncrypted, "Het veld 'dataEncrypted' is leeg! De encryptie-listener heeft niets gedaan.");
        assertNotNull(locationEncrypted, "Het veld 'locationEncrypted' is leeg!");

        assertFalse(dataEncrypted.contains(plainPhone),
                "Fout: Het telefoonnummer is leesbaar aanwezig in de dataEncrypted string!");

        mongoTemplate.remove(appointment);
    }
}