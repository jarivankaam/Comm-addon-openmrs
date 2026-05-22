package com.azaricomm.service;

import com.azaricomm.model.Appointment;
import com.azaricomm.model.EncryptedAppointmentData;
import com.azaricomm.model.NotificationMessage;
import com.azaricomm.repository.AppointmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AppointmentEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentEnrichmentService.class);

    private final AppointmentRepository appointmentRepository;
    private final DecryptionService decryptionService;
    private final ObjectMapper objectMapper;

    public AppointmentEnrichmentService(AppointmentRepository appointmentRepository,
                                        DecryptionService decryptionService,
                                        ObjectMapper objectMapper) {
        this.appointmentRepository = appointmentRepository;
        this.decryptionService = decryptionService;
        this.objectMapper = objectMapper;
    }

    private String buildBody(NotificationMessage message) {
        StringBuilder body = new StringBuilder();

        boolean is1h = "REMINDER_1H".equalsIgnoreCase(message.getNotificationType());
        body.append(is1h
                ? "Herinnering: U heeft over 1 uur een afspraak."
                : "Herinnering: U heeft morgen een afspraak.");

        if (message.getAppointmentDateTime() != null) {
            body.append("\nTijd: ").append(message.getAppointmentDateTime());
        }
        if (message.getAppointmentLocation() != null) {
            body.append("\nLocatie: ").append(message.getAppointmentLocation());
        }
        if (message.getInstructions() != null && !message.getInstructions().isBlank()) {
            body.append("\nInstructies: ").append(message.getInstructions());
        }

        return body.toString();
    }

    /**
     * Looks up the appointment by ID from MongoDB and populates the message
     * with decrypted patient data, provider, timezone, and scheduled time.
     *
     * @return false if the appointment cannot be found or decrypted
     */
    public boolean enrich(NotificationMessage message) {
        String appointmentId = message.getAppointmentId();

        Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            log.warn("Appointment not found in MongoDB for id: {}", appointmentId);
            return false;
        }

        try {
            String decryptedJson = decryptionService.decrypt(appointment.getDataEncrypted());
            EncryptedAppointmentData data = objectMapper.readValue(decryptedJson, EncryptedAppointmentData.class);

            message.setPatientId(data.getPatientId());
            message.setPatientPhone(data.getPatientPhone());
            message.setSubject(data.getSubject());
            message.setInstructions(data.getInstructions());

            // Provider from DB takes precedence over the queue message
            if (data.getProvider() != null && !data.getProvider().isBlank()) {
                message.setProvider(data.getProvider());
            }
        } catch (Exception e) {
            log.error("Failed to decrypt appointment data for id: {}", appointmentId, e);
            return false;
        }

        if (appointment.getLocationEncrypted() != null) {
            try {
                message.setAppointmentLocation(decryptionService.decrypt(appointment.getLocationEncrypted()));
            } catch (Exception e) {
                log.warn("Failed to decrypt location for appointment id: {}", appointmentId, e);
            }
        }

        message.setOrganizationId(appointment.getOrganizationId());
        message.setTimezone(appointment.getTimezone());

        if (appointment.getScheduledTime() != null) {
            message.setAppointmentDateTime(appointment.getScheduledTime().toString());
        }

        message.setBody(buildBody(message));

        log.debug("Enriched notification message from MongoDB for appointment: {}", appointmentId);
        return true;
    }
}
