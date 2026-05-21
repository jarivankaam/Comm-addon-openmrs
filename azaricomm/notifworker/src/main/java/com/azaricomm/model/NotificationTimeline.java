package com.azaricomm.model;

public class NotificationTimeline {

    private String reminder24h = "SCHEDULED";
    private String reminder1h = "SCHEDULED";

    public String getReminder24h() { return reminder24h; }
    public void setReminder24h(String reminder24h) { this.reminder24h = reminder24h; }

    public String getReminder1h() { return reminder1h; }
    public void setReminder1h(String reminder1h) { this.reminder1h = reminder1h; }
}
