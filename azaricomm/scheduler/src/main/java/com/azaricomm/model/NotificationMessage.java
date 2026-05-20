package com.azaricomm.model;

import java.io.Serializable;

public class NotificationMessage implements Serializable {
    private String appointmentId;
    private String reminderType; // "24h" of "1h"

    public NotificationMessage() {}

    public NotificationMessage(String appointmentId, String reminderType) {
        this.appointmentId = appointmentId;
        this.reminderType = reminderType;
    }

    // Getters en Setters
    public String getAppointmentId() { return appointmentId; }
    public void setAppointmentId(String appointmentId) { this.appointmentId = appointmentId; }

    public String getReminderType() { return reminderType; }
    public void setReminderType(String reminderType) { this.reminderType = reminderType; }
}