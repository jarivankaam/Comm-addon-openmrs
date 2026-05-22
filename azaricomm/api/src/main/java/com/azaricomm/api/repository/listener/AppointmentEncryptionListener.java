package com.azaricomm.api.repository.listener;

import com.azaricomm.api.model.Appointment;
import com.azaricomm.api.model.EncryptedData;
import com.azaricomm.api.service.EncryptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;
import org.springframework.stereotype.Component;

@Component
public class AppointmentEncryptionListener extends AbstractMongoEventListener<Appointment> {

    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;

    public AppointmentEncryptionListener(EncryptionService encryptionService, ObjectMapper objectMapper) {
        this.encryptionService = encryptionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onBeforeConvert(BeforeConvertEvent<Appointment> event) {
        Appointment appointment = event.getSource();

        try {
            // Bundle all sensitive data together
            EncryptedData encryptedData = new EncryptedData(
                    appointment.getPatientPhone(),
                    appointment.getSubject(),
                    appointment.getInstructions(),
                    appointment.getProvider()
            );

            // Make it json and encrypt it.
            String json = objectMapper.writeValueAsString(encryptedData);
            appointment.setDataEncrypted(encryptionService.encrypt(json));

            // Encrypt location separately, so you can use it better in the future.
            if (appointment.getLocation() != null) {
                appointment.setLocationEncrypted(encryptionService.encrypt(appointment.getLocation()));
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt appointment during lifecycle event", e);
        }
    }
}