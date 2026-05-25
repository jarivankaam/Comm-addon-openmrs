package com.azaricomm.consumer;

import com.azaricomm.model.DeliveryResult;
import com.azaricomm.model.NotificationMessage;
import com.azaricomm.provider.ProviderRouter;
import com.azaricomm.service.AppointmentEnrichmentService;
import com.azaricomm.service.NotificationRetryService;
import com.azaricomm.service.NotificationValidationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock
    private AppointmentEnrichmentService enrichmentService;

    @Mock
    private ProviderRouter providerRouter;

    @Mock
    private NotificationRetryService retryService;

    // FIX 1: Voeg de missende mock toe om de NullPointerException te voorkomen
    @Mock
    private NotificationValidationService validationService;

    @InjectMocks
    private NotificationConsumer notificationConsumer;

    @Test
    void testHandleNotification_Success() {
        // Arrange
        NotificationMessage message = new NotificationMessage();
        message.setAppointmentId("6a1054a33c20b15d40b54ff7");
        message.setNotificationType("REMINDER_1H");
        message.setProvider("swiftsend");
        message.setOrganizationId("org-azaricomm-01");
        message.setPatientId("pat-openmrs-999");
        message.setPatientPhone("+31612345678");
        message.setSubject("Swiftsend Test");

        when(enrichmentService.enrich(any(NotificationMessage.class))).thenReturn(true);

        // FIX 2: Zorg dat de validatie slaagt
        when(validationService.validateMessage(any(NotificationMessage.class))).thenReturn(true);

        when(providerRouter.route(any(NotificationMessage.class)))
                .thenReturn(DeliveryResult.success("swiftsend", "SS-123"));

        // Act
        notificationConsumer.handleNotification(message);

        // Assert
        verify(enrichmentService, times(1)).enrich(message);
        verify(validationService, times(1)).validateMessage(message);
        verify(providerRouter, times(1)).route(message);
        verify(retryService, times(1)).markNotificationAsSent("6a1054a33c20b15d40b54ff7", "REMINDER_1H");
        verify(retryService, never()).saveFailedNotification(any(), any(), any());
    }

    @Test
    void testHandleNotification_ProviderFailure_ShouldThrowException() {
        // Arrange
        NotificationMessage message = new NotificationMessage();
        message.setAppointmentId("6a1056ef3c20b15d40b54ff9");
        message.setNotificationType("REMINDER_1H");
        message.setProvider("swiftsend");
        message.setOrganizationId("org-azaricomm-01");
        message.setPatientId("pat-openmrs-999");
        message.setPatientPhone("+31612345678");

        when(enrichmentService.enrich(any(NotificationMessage.class))).thenReturn(true);

        // Ook hier moet de validatie op true staan
        when(validationService.validateMessage(any(NotificationMessage.class))).thenReturn(true);

        // We simuleren de specifieke foutmelding van de provider
        String expectedErrorMessage = "HTTP 503 SERVICE_UNAVAILABLE";
        when(providerRouter.route(any(NotificationMessage.class)))
                .thenReturn(DeliveryResult.failure("swiftsend", expectedErrorMessage));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            notificationConsumer.handleNotification(message);
        });

        // FIX 3: Verifieer met de juiste String-parameters (appointmentId, type, errorMessage)
        verify(retryService, times(1)).saveFailedNotification(
                eq("6a1056ef3c20b15d40b54ff9"),
                eq("REMINDER_1H"),
                eq(expectedErrorMessage)
        );
        verify(retryService, never()).markNotificationAsSent(any(), any());
    }
}