package com.azaricomm.api.mapper;

import com.azaricomm.api.client.OpenMrsClient;
import com.azaricomm.api.model.Appointment;
import com.azaricomm.api.model.AppointmentStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class FhirAppointmentMapperTest {

    @Mock
    private OpenMrsClient openMrsClient;

    private FhirAppointmentMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mapper = new FhirAppointmentMapper(objectMapper, openMrsClient);
    }

    @Test
    void map_WithValidJson_ShouldMapCorrectly() throws IOException {
        String json = """
        {
          "id": "fhir-app-123",
          "status": "booked",
          "start": "2026-05-21T20:00:00+02:00",
          "serviceType": [{
            "coding": [{ "display": "Tandheelkunde" }]
          }],
          "participant": [
            {
              "actor": {
                "type": "Organization",
                "reference": "Organization/org-abc",
                "display": "Hoofdkliniek"
              }
            }
          ]
        }
        """;

        Appointment appointment = mapper.map(json);

        assertNotNull(appointment);
        assertEquals("fhir-app-123", appointment.getPatientId());
        assertEquals(AppointmentStatus.SCHEDULED, appointment.getStatus());
        assertEquals("Tandheelkunde", appointment.getLocation());
        assertEquals("Hoofdkliniek", appointment.getOrganizationId());
        assertNotNull(appointment.getScheduledTime());
    }

    @Test
    void map_WithInvalidDateString_ShouldThrowIllegalArgumentException() {
        String json = "{\"start\": \"ongeldige-datum\"}";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            mapper.map(json);
        });

        assertTrue(exception.getMessage().contains("Invalid date format"));
    }

    @Test
    void map_WithMissingDate_ShouldThrowIllegalArgumentException() {
        String json = "{\"id\": \"app-123\"}";

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            mapper.map(json);
        });

        assertTrue(exception.getMessage().contains("cannot be null or blank"));
    }
}