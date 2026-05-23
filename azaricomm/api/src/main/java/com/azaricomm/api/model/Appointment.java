package com.azaricomm.api.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

@Document(collection = "appointments")
@CompoundIndexes({
        @CompoundIndex(name = "patient_schedule_idx", def = "{'patientId': 1, 'scheduledTime': 1}", unique = true)
})
public class Appointment {

    @Id
    private String id;

    private String organizationId;
    @Transient private String organizationName;
    private Instant scheduledTime;

    private String patientId;

    private String dataEncrypted;
    private String locationEncrypted;

    private String timezone;

    @NotNull(message = "Status can only be: SCHEDULED, QUEUED, SENT, CANCELLED, or FAILED")
    private AppointmentStatus status;
    private NotificationTimeline notifications = new NotificationTimeline();
    private Instant createdAt;

    @Indexed(expireAfterSeconds = 0)
    private Instant expireAt;

    // @transient makes it "invisible" for the database
    @Transient private String patientPhone;
    @Transient private String subject = "Afspraakherinnering";;
    @Transient private String location;
    @Transient private String instructions;
    @Transient private String provider;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public String getOrganizationName() { return organizationName; }
    public void setOrganizationName(String organizationName) { this.organizationName = organizationName; }

    public String getPatientId() { return patientId; }
    public void setPatientId(String patientId) { this.patientId = patientId; }

    public String getDataEncrypted() { return dataEncrypted; }
    public void setDataEncrypted(String dataEncrypted) { this.dataEncrypted = dataEncrypted; }
    public String getLocationEncrypted() { return locationEncrypted; }
    public void setLocationEncrypted(String locationEncrypted) { this.locationEncrypted = locationEncrypted; }

    public String getTimezone() { return timezone;}
    public void setTimezone(String timezone) {this.timezone = timezone;}

    public AppointmentStatus getStatus() { return status; }

    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    // In case it isn't send as an enum.
    public void setStatus(String status) {
        if (status != null) {
            try {
                this.status = AppointmentStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                this.status = null;
            }
        }
    }

    public NotificationTimeline getNotifications() { return notifications; }
    public void setNotifications(NotificationTimeline notifications) { this.notifications = notifications; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpireAt() {return expireAt;}

    public void setExpireAt(Instant expireAt) {this.expireAt = expireAt;}

    // Transient getters/setters
    public String getPatientPhone() { return patientPhone; }
    public void setPatientPhone(String patientPhone) { this.patientPhone = patientPhone; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public Instant getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(Instant scheduledTime) { this.scheduledTime = scheduledTime; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
}