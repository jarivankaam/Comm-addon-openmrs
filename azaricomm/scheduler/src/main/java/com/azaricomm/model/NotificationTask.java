package com.azaricomm.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;

@Document(collection = "appointments") // Aangepast naar "appointments" conform je diagram
@CompoundIndex(name = "status_scheduledTime_idx", def = "{'status': 1, 'scheduledTime': 1}")
public class NotificationTask {

    @Id
    private String id;
    private String appointmentId;  // hospital_id / appointment_id uit je diagram

    // De TTL-index zorgt ervoor dat Mongo dit document 14 dagen na 'scheduledTime' automatisch wist
    @Indexed(name = "ttl_scheduled_time", expireAfter = "14d")
    private Instant scheduledTime;

    private String status;         // PENDING, QUEUED, SENT, FAILED, CANCELLED
    private String providerId;     // twilioprovider etc.

    // Hier slaan we de versleutelde gevoelige data op, precies zoals in je diagram!
    private String dataEncrypted;

    public NotificationTask() {}

    // Getters en Setters
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
}