package com.azaricomm.api.dto;

import com.azaricomm.api.model.AppointmentStatus;
import java.time.Instant;

public class AppointmentCreatedResponse {
    private String id;
    private AppointmentStatus status;
    private Instant createdAt;
    private String message;

    public AppointmentCreatedResponse(String id, AppointmentStatus status, Instant createdAt, String message) {
        this.id = id;
        this.status = status;
        this.createdAt = createdAt;
        this.message = message;
    }
    
    public String getId() { return id; }
    public AppointmentStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public String getMessage() { return message; }
}