package com.azaricomm.api.service;

import com.azaricomm.api.dto.AppointmentCreatedResponse;
import com.azaricomm.api.dto.CreateAppointmentRequest;
import com.azaricomm.api.mapper.FhirAppointmentMapper;
import com.azaricomm.api.metrics.ApiMetrics;
import com.azaricomm.api.model.Appointment;
import com.azaricomm.api.model.AppointmentStatus;
import com.azaricomm.api.repository.AppointmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceImplTest {

    @Mock private AppointmentRepository repository;
    @Mock private FhirAppointmentMapper fhirMapper;
    @Mock private ApiMetrics apiMetrics;
    @Mock private Validator validator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private AppointmentServiceImpl appointmentService;

    @BeforeEach
    void setUp() {
        appointmentService = new AppointmentServiceImpl(repository, fhirMapper, apiMetrics, validator, objectMapper);
    }

    @Test
    void create_ShouldSaveAppointment_AndRecordMetrics() {
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setOrganizationId("org-1");
        request.setScheduledTime(Instant.now().plus(2, ChronoUnit.DAYS));
        request.setPatientId("pat-1");

        Appointment savedAppointment = new Appointment();
        savedAppointment.setId("mongo-id-999");
        savedAppointment.setStatus(AppointmentStatus.SCHEDULED);
        savedAppointment.setCreatedAt(Instant.now());

        when(repository.save(any(Appointment.class))).thenReturn(savedAppointment);

        AppointmentCreatedResponse response = appointmentService.create(request);

        assertNotNull(response);
        assertEquals("mongo-id-999", response.getId());
        verify(repository, times(1)).save(any(Appointment.class));
        verify(apiMetrics, times(1)).recordAppointmentReceived("org-1");
    }

    @Test
    void cancel_WhenAppointmentAlreadyCancelled_ShouldThrowBadRequest() {
        Appointment cancelledAppointment = new Appointment();
        cancelledAppointment.setId("app-1");
        cancelledAppointment.setStatus(AppointmentStatus.CANCELLED);

        when(repository.findById("app-1")).thenReturn(Optional.of(cancelledAppointment));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            appointmentService.cancel("app-1");
        });
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("This appointment is already cancelled.", ex.getReason());
    }

    @Test
    void cancel_WhenRetentionPeriodExceeded_ShouldThrowBadRequest() {
        Appointment oldAppointment = new Appointment();
        oldAppointment.setId("app-2");
        oldAppointment.setStatus(AppointmentStatus.SCHEDULED);
        oldAppointment.setExpireAt(Instant.now().plus(1, ChronoUnit.HOURS));

        when(repository.findById("app-2")).thenReturn(Optional.of(oldAppointment));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            appointmentService.cancel("app-2");
        });
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("would exceed the original privacy lifecycle"));
    }

    @Test
    void cancel_ShouldSucceed_WhenWithinPrivacyLifecycle() {
        Appointment appointment = new Appointment();
        appointment.setId("app-3");
        appointment.setOrganizationId("org-1");
        appointment.setStatus(AppointmentStatus.SCHEDULED);
        appointment.setExpireAt(Instant.now().plus(30, ChronoUnit.DAYS));

        when(repository.findById("app-3")).thenReturn(Optional.of(appointment));
        when(repository.save(any(Appointment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Appointment result = appointmentService.cancel("app-3");

        assertEquals(AppointmentStatus.CANCELLED, result.getStatus());
        verify(apiMetrics, times(1)).recordAppointmentCancelled("org-1");
    }

    @Test
    void processWebhook_WhenValidationFails_ShouldThrowBadRequest() {
        String mockJson = "{}";
        Appointment mockMappedAppointment = new Appointment();
        try {
            when(fhirMapper.map(mockJson)).thenReturn(mockMappedAppointment);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Set<ConstraintViolation<CreateAppointmentRequest>> violations = new HashSet<>();
        ConstraintViolation<CreateAppointmentRequest> violation = mock(ConstraintViolation.class);
        when(violation.getPropertyPath()).thenReturn(mock(jakarta.validation.Path.class));
        when(violation.getMessage()).thenReturn("is required");
        violations.add(violation);

        when(validator.validate(any(CreateAppointmentRequest.class))).thenReturn(violations);

        assertThrows(ResponseStatusException.class, () -> {
            appointmentService.processWebhook(mockJson, "127.0.0.1", null, 0, 0);
        });
        verify(repository, never()).save(any());
    }
}