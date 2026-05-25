package com.azaricomm.model;

import java.io.Serializable;

public class NotificationMessage implements Serializable {

    private String appointmentId;
    private String notificationType;

    public NotificationMessage() {}

    public NotificationMessage(String appointmentId, String notificationType) {
        this.appointmentId = appointmentId;
        this.notificationType = notificationType;
    }

    public String getAppointmentId() { return appointmentId; }
    public void setAppointmentId(String appointmentId) { this.appointmentId = appointmentId; }

    public String getNotificationType() { return notificationType; }
    public void setNotificationType(String notificationType) { this.notificationType = notificationType; }
}