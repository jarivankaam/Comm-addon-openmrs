package com.azaricomm.api.service;

import com.azaricomm.api.client.OpenMrsClient;
import com.azaricomm.api.model.Appointment;
import com.azaricomm.api.model.Provider;
import com.azaricomm.api.repository.AppointmentRepository;
import com.azaricomm.api.repository.ProviderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "OPENMRS_BASE_URL=http://localhost:8080",
        "OPENMRS_USERNAME=test",
        "OPENMRS_PASSWORD=test",
        "WEBHOOK_SECRET=test-secret-12345678901234567890123456789012",
        "CRYPTO_SECRET_KEY=DitIsMijnSuperGeheimeSleutel123!"
})
class WebhookIdempotencyIntegrationTest {

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OpenMrsClient openMrsClient;

    @BeforeEach
    void setUp() {
        Provider testProvider = new Provider();
        testProvider.setId("dr-vandersteen");
        testProvider.setName("Dr. Vandersteen");
        testProvider.setActive(true);
        providerRepository.save(testProvider);
    }

    @AfterEach
    void tearDown() {
        appointmentRepository.findAll().stream()
                .filter(a -> "patient-uuid-999".equals(a.getPatientId()))
                .forEach(a -> appointmentRepository.deleteById(a.getId()));

        providerRepository.deleteById("dr-vandersteen");
    }

    @Test
    void whenDuplicateWebhookReceived_ShouldHandleIdempotentlyWithoutCrashing() {
        String fhirJson = """
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

        ObjectNode mockPatientNode = objectMapper.createObjectNode();
        ObjectNode mockPersonNode = mockPatientNode.putObject("person");
        mockPersonNode.putArray("attributes"); // Voorkomt NullPointerException in extractPhone

        when(openMrsClient.getPatientByIdentifier("PAT-101")).thenReturn(mockPatientNode);
        when(openMrsClient.extractPhone(mockPatientNode)).thenReturn("+31612345678");
        when(openMrsClient.extractUuid(mockPatientNode)).thenReturn("patient-uuid-999");
        when(openMrsClient.getOrganizationName("org-456")).thenReturn("Main Hospital Corp");

        assertDoesNotThrow(() -> {
            appointmentService.processWebhook(fhirJson, "127.0.0.1", "utf-8", fhirJson.length(), fhirJson.length());
        }, "De eerste webhook-aanroep faalde onverwacht.");

        Optional<Appointment> saved = appointmentRepository.findAll().stream()
                .filter(a -> "patient-uuid-999".equals(a.getPatientId()))
                .findFirst();

        assertTrue(saved.isPresent(), "De afspraak had opgeslagen moeten zijn in de database.");

        assertDoesNotThrow(() -> {
            appointmentService.processWebhook(fhirJson, "127.0.0.1", "utf-8", fhirJson.length(), fhirJson.length());
        }, "De applicatie crashte op een dubbele webhook-aanroep (idempotentie faalt).");
    }
}