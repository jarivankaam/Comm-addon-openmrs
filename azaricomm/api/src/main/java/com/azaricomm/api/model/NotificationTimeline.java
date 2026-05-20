package com.azaricomm.api.model;

import jakarta.validation.constraints.NotNull;

public class NotificationTimeline {

    @NotNull
    private AppointmentStatus reminder24h = AppointmentStatus.SCHEDULED;

    @NotNull
    private AppointmentStatus reminder1h = AppointmentStatus.SCHEDULED;

    // Getters en Setters
    public AppointmentStatus getReminder24h() { return reminder24h; }
    public void setReminder24h(AppointmentStatus reminder24h) { this.reminder24h = reminder24h; }

    public AppointmentStatus getReminder1h() { return reminder1h; }
    public void setReminder1h(AppointmentStatus reminder1h) { this.reminder1h = reminder1h; }
}