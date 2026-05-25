package com.azaricomm.service;

import com.azaricomm.model.AuditLog;
import com.azaricomm.model.DeliveryResult;
import com.azaricomm.model.NotificationMessage;
import com.azaricomm.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void logDeliveryAttempt(NotificationMessage message, DeliveryResult result, Instant receivedAt) {
        try {
            AuditLog auditLog = new AuditLog();
            Instant now = Instant.now();

            auditLog.setAppointmentId(message.getAppointmentId());
            auditLog.setOrganizationId(message.getOrganizationId());
            auditLog.setNotificationType(message.getNotificationType());
            auditLog.setProvider(result.getProviderName());
            auditLog.setProviderMessageId(result.getProviderMessageId());

            auditLog.setSuccess(result.isSuccess());
            auditLog.setErrorMessage(result.getErrorMessage());

            auditLog.setReceivedAt(receivedAt);
            auditLog.setSentAt(now);

            auditLog.setExpireAt(now.plus(365, ChronoUnit.DAYS));

            auditLogRepository.save(auditLog);
            log.debug("Audit log saved for appointment: {}", message.getAppointmentId());

        } catch (Exception e) {
            log.error("Failed to save audit log for appointment {}", message.getAppointmentId(), e);
        }
    }
}