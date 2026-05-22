package com.azaricomm.api.service;

import com.azaricomm.api.client.OpenMrsClient;
import com.azaricomm.api.model.Appointment;
import com.azaricomm.api.model.AppointmentStatus;
import com.azaricomm.api.repository.AppointmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "OPENMRS_BASE_URL=http://localhost:8080",
        "OPENMRS_USERNAME=test",
        "OPENMRS_PASSWORD=test",
        "WEBHOOK_SECRET=test-secret-12345678901234567890123456789012",
        "CRYPTO_SECRET_KEY=DitIsMijnSuperGeheimeSleutel123!"
})
class WebhookIntegrationTest {

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OpenMrsClient openMrsClient;

    private String createValidFhirJson() {
        return """
        {
          "resourceType": "Appointment",
          "id": "openmrs-appt-123",
          "status": "booked",
          "start": "2027-05-22T10:00:00+02:00",
          "serviceType": [
            {
              "coding": [
                {
                  "display": "General Medicine Clinic"
                }
              ]
            }
          ],
          "participant": [
            {
              "actor": {
                "type": "Patient",
                "reference": "Patient/patient-uuid-999",
                "identifier": {
                  "value": "PAT-101"
                }
              }
            },
            {
              "actor": {
                "type": "Organization",
                "reference": "Organization/org-456"
              }
            }
          ],
          "extension": [
            {
              "url": "http://openmrs.org/fhir/extension/timezone",
              "valueString": "+02:00"
            },
            {
              "url": "http://openmrs.org/fhir/extension/messageProvider",
              "valueString": "dr-vandersteen"
            }
          ]
        }
        """;
    }

    @Test
    void whenValidWebhookReceived_ShouldMapAndSaveAppointment() throws IOException {
        String fhirJson = createValidFhirJson();

        ObjectNode mockPatientNode = objectMapper.createObjectNode();
        when(openMrsClient.getPatientByIdentifier("PAT-101")).thenReturn(mockPatientNode);
        when(openMrsClient.extractPhone(mockPatientNode)).thenReturn("+31612345678");
        when(openMrsClient.extractUuid(mockPatientNode)).thenReturn("patient-uuid-999");
        when(openMrsClient.getOrganizationName("org-456")).thenReturn("Main Hospital Corp");

        try {
            appointmentService.processWebhook(fhirJson, "127.0.0.1", "utf-8", fhirJson.length(), fhirJson.length());

            Optional<Appointment> saved = appointmentRepository.findById("patient-uuid-999");
            assertTrue(saved.isPresent());

            Appointment appt = saved.get();
            assertEquals(AppointmentStatus.SCHEDULED, appt.getStatus());
            assertEquals("+31612345678", appt.getPatientPhone());
            assertEquals("Main Hospital Corp", appt.getOrganizationId());
            assertEquals("dr-vandersteen", appt.getProvider());
            assertEquals("+02:00", appt.getTimezone());
            assertEquals("General Medicine Clinic", appt.getLocation());

            appointmentRepository.delete(appt);

        } catch (ResponseStatusException e) {
            assertEquals(400, e.getStatusCode().value());
            assertTrue(e.getReason().contains("provider"));
        }
    }

    @Test
    void whenWebhookHasMissingRequiredFields_ShouldThrowBadRequestException() {
        String invalidFhirJson = """
        {
          "resourceType": "Appointment",
          "id": "openmrs-appt-123",
          "status": "booked"
        }
        """;

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            appointmentService.processWebhook(invalidFhirJson, "127.0.0.1", "utf-8", invalidFhirJson.length(), invalidFhirJson.length());
        });

        assertTrue(exception.getMessage().contains("Scheduled time cannot be null or blank"));
    }
}