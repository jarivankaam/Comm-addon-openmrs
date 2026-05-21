package com.azaricomm.api.service;

import com.azaricomm.api.dto.AppointmentCreatedResponse;
import com.azaricomm.api.dto.CreateAppointmentRequest;
import com.azaricomm.api.model.Appointment;

import java.io.IOException;
import java.util.Optional;

public interface AppointmentService {
    AppointmentCreatedResponse create(CreateAppointmentRequest request);
    Optional<Appointment> findById(String id);
    Appointment cancel(String id);
    void processWebhook(String json, String remoteAddr, String encoding, int rawLen, int bodyLen) throws IOException;
}
