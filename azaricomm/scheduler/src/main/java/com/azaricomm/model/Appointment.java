package com.azaricomm.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.Map;

@Document(collection = "appointments")
@CompoundIndex(name = "status_scheduledTime_idx", def = "{'status': 1, 'scheduledTime': 1}")
public class Appointment {

    @Id
    private String id;
    private Instant scheduledTime;
    private String status;

    private NotificationTimers notifications = new NotificationTimers();
    private Map<String, String> traceContext;

    public Appointment() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Instant getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(Instant scheduledTime) { this.scheduledTime = scheduledTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public NotificationTimers getNotifications() { return notifications; }
    public void setNotifications(NotificationTimers notifications) { this.notifications = notifications; }

    public Map<String, String> getTraceContext() { return traceContext; }
    public void setTraceContext(Map<String, String> traceContext) { this.traceContext = traceContext; }
}