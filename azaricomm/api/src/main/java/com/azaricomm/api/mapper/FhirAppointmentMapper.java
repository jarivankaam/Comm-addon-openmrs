package com.azaricomm.api.mapper;

import com.azaricomm.api.client.OpenMrsClient;
import com.azaricomm.api.model.Appointment;
import com.azaricomm.api.model.AppointmentStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class FhirAppointmentMapper {

    private static final Logger log = LoggerFactory.getLogger(FhirAppointmentMapper.class);

    private final ObjectMapper objectMapper;
    private final OpenMrsClient openMrsClient;

    public FhirAppointmentMapper(ObjectMapper objectMapper, OpenMrsClient openMrsClient) {
        this.objectMapper = objectMapper;
        this.openMrsClient = openMrsClient;
    }

    public Appointment map(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);

        Appointment appointment = new Appointment();
        appointment.setCreatedAt(Instant.now());
        appointment.setPatientId(root.path("id").asText(null));

        mapServiceTypeLocation(appointment, root.path("serviceType"));
        mapStartTime(appointment, root.path("start").asText(null));
        appointment.setStatus(mapStatus(root.path("status").asText("booked")));
        mapParticipants(appointment, root.path("participant"));
        mapExtensions(appointment, root.path("extension"));

        return appointment;
    }

    private void mapServiceTypeLocation(Appointment appointment, JsonNode serviceType) {
        if (!serviceType.isArray() || serviceType.size() == 0) return;
        JsonNode coding = serviceType.get(0).path("coding");
        if (!coding.isArray() || coding.size() == 0) return;
        String display = coding.get(0).path("display").asText(null);
        if (display != null && !display.isBlank()) {
            appointment.setLocation(display);
        }
    }

    private void mapStartTime(Appointment appointment, String fhirStartText) {
        if (fhirStartText == null || fhirStartText.isBlank() || "null".equalsIgnoreCase(fhirStartText)) {
            throw new IllegalArgumentException("Scheduled time cannot be null or blank");
        }

        try {
            OffsetDateTime odt = OffsetDateTime.parse(fhirStartText);
            Instant parsedTime = odt.toInstant();
            appointment.setScheduledTime(parsedTime);
            appointment.setExpireAt(parsedTime.plus(14, ChronoUnit.DAYS));
        } catch (Exception e) {
            log.error("Failed to parse OpenMRS scheduledTime string: {}", fhirStartText, e);
            throw new IllegalArgumentException("Invalid date format received from OpenMRS: " + fhirStartText, e);
        }
    }

    private void mapParticipants(Appointment appointment, JsonNode participants) {
        if (!participants.isArray()) return;
        for (JsonNode participant : participants) {
            JsonNode actor = participant.path("actor");
            String type = actor.path("type").asText("");
            String display = actor.path("display").asText("");
            String reference = actor.path("reference").asText("");
            String id = reference.contains("/") ? reference.substring(reference.lastIndexOf('/') + 1) : reference;

            if ("Patient".equalsIgnoreCase(type)) {
                mapPatientParticipant(appointment, actor, id);
            } else if ("Location".equalsIgnoreCase(type) || "HealthcareService".equalsIgnoreCase(type)) {
                appointment.setLocation(display);
            } else if ("Organization".equalsIgnoreCase(type) && !reference.isBlank()) {
                String orgName = display.isBlank() ? openMrsClient.getOrganizationName(id) : display;
                appointment.setOrganizationId(orgName);
            }
        }
    }

    private void mapPatientParticipant(Appointment appointment, JsonNode actor, String refId) {
        appointment.setPatientId(refId);
        String identifier = actor.path("identifier").path("value").asText(null);
        if (identifier == null) identifier = refId;

        JsonNode patient = openMrsClient.getPatientByIdentifier(identifier);
        String phone = openMrsClient.extractPhone(patient);
        String uuid = openMrsClient.extractUuid(patient);

        if (uuid != null) appointment.setPatientId(uuid);
        appointment.setPatientPhone(phone);
        appointment.setSubject("Afspraakherinnering");
    }

    private void mapExtensions(Appointment appointment, JsonNode extensions) {
        if (!extensions.isArray()) return;
        for (JsonNode ext : extensions) {
            String url = ext.path("url").asText("");
            String value = ext.path("valueString").asText(null);
            if (value == null || value.isBlank()) continue;

            if ("http://openmrs.org/fhir/extension/timezone".equals(url)) {
                appointment.setTimezone(value);
            } else if ("http://openmrs.org/fhir/extension/messageProvider".equals(url)) {
                appointment.setProvider(value.toLowerCase());
            }
        }
    }

    private static AppointmentStatus mapStatus(String fhirStatus) {
        return switch (fhirStatus.toLowerCase()) {
            case "booked", "pending" -> AppointmentStatus.SCHEDULED;
            case "cancelled", "noshow" -> AppointmentStatus.CANCELLED;
            case "fulfilled" -> AppointmentStatus.SENT;
            default -> AppointmentStatus.SCHEDULED;
        };
    }
}
