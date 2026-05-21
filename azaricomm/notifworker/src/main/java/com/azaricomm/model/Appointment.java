package com.azaricomm.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "appointments")
public class Appointment {

    @Id
    private String id;

    private String organizationId;
    private Instant scheduledTime;
    private String dataEncrypted;
    private String locationEncrypted;
    private String timezone;
    private NotificationTimeline notifications = new NotificationTimeline();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }

    public Instant getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(Instant scheduledTime) { this.scheduledTime = scheduledTime; }

    public String getDataEncrypted() { return dataEncrypted; }
    public void setDataEncrypted(String dataEncrypted) { this.dataEncrypted = dataEncrypted; }

    public String getLocationEncrypted() { return locationEncrypted; }
    public void setLocationEncrypted(String locationEncrypted) { this.locationEncrypted = locationEncrypted; }

    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }

    public NotificationTimeline getNotifications() { return notifications; }
    public void setNotifications(NotificationTimeline notifications) { this.notifications = notifications; }
}
