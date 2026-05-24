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
    private String appointmentId;  // OpenMRS appointment ID
    private Instant scheduledTime; // De datum/tijd van de afspraak zelf (Instant!)
    private String status;         // SCHEDULED, CANCELLED, etc.
    private String providerId;     // swiftsend, legacylink, asyncflow, securepost
    private String dataEncrypted;  // Het versleutelde AVG-blok met patiëntinfo (de worker pakt dit straks uit!)
    private Instant createdAt;

    // Jouw slimme notificatie-timers structuur voor de 24h en 1h herinneringen
    private NotificationTimers notifications = new NotificationTimers();
    private Map<String, String> traceContext;

    public Appointment() {}

    // --- Getters and Setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAppointmentId() { return appointmentId; }
    public void setAppointmentId(String appointmentId) { this.appointmentId = appointmentId; }

    public Instant getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(Instant scheduledTime) { this.scheduledTime = scheduledTime; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public String getDataEncrypted() { return dataEncrypted; }
    public void setDataEncrypted(String dataEncrypted) { this.dataEncrypted = dataEncrypted; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public NotificationTimers getNotifications() { return notifications; }
    public void setNotifications(NotificationTimers notifications) { this.notifications = notifications; }

    public Map<String, String> getTraceContext() { return traceContext; }
    public void setTraceContext(Map<String, String> traceContext) { this.traceContext = traceContext; }
}
