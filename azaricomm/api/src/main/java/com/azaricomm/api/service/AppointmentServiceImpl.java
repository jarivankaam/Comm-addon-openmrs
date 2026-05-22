package com.azaricomm.api.service;

import com.azaricomm.api.dto.AppointmentCreatedResponse;
import com.azaricomm.api.dto.CreateAppointmentRequest;
import com.azaricomm.api.mapper.FhirAppointmentMapper;
import com.azaricomm.api.metrics.ApiMetrics;
import com.azaricomm.api.model.Appointment;
import com.azaricomm.api.model.AppointmentStatus;
import com.azaricomm.api.repository.AppointmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class AppointmentServiceImpl implements AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentServiceImpl.class);

    private final AppointmentRepository repository;
    private final FhirAppointmentMapper fhirMapper;
    private final ApiMetrics apiMetrics;
    private final Validator validator;
    private final ObjectMapper objectMapper;

    public AppointmentServiceImpl(AppointmentRepository repository, FhirAppointmentMapper fhirMapper,
                                  ApiMetrics apiMetrics, Validator validator, ObjectMapper objectMapper) {
        this.repository = repository;
        this.fhirMapper = fhirMapper;
        this.apiMetrics = apiMetrics;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    @Override
    public AppointmentCreatedResponse create(CreateAppointmentRequest request) {
        Appointment appointment = new Appointment();
        appointment.setOrganizationId(request.getOrganizationId());
        appointment.setScheduledTime(request.getScheduledTime());
        appointment.setPatientId(request.getPatientId());
        appointment.setPatientPhone(request.getPatientPhone());
        appointment.setSubject(request.getSubject());
        appointment.setLocation(request.getLocation());
        appointment.setInstructions(request.getInstructions());
        appointment.setProvider(request.getProvider());
        appointment.setTimezone(request.getTimezone());
        appointment.setStatus(AppointmentStatus.SCHEDULED);
        appointment.setCreatedAt(Instant.now());
        appointment.setExpireAt(request.getScheduledTime().plus(14, ChronoUnit.DAYS));
        appointment.setTraceContext(currentTraceContext());

        Appointment saved = repository.save(appointment);
        apiMetrics.recordAppointmentReceived(request.getOrganizationId());

        return new AppointmentCreatedResponse(saved.getId(), saved.getStatus(), saved.getCreatedAt(),
                "Appointment successfully created");
    }

    @Override
    public Optional<Appointment> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public Appointment cancel(String id) {
        return repository.findById(id)
                .map(a -> {
                    if (a.getStatus() == AppointmentStatus.CANCELLED) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "This appointment is already cancelled.");
                    }
                    Instant newExpireAt = Instant.now().plus(14, ChronoUnit.DAYS);
                    if (a.getExpireAt() != null && newExpireAt.isAfter(a.getExpireAt())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Cannot cancel appointment: the new retention period would exceed the original privacy lifecycle.");
                    }
                    a.setStatus(AppointmentStatus.CANCELLED);
                    a.setExpireAt(newExpireAt);
                    Appointment saved = repository.save(a);
                    apiMetrics.recordAppointmentCancelled(a.getOrganizationId());
                    return saved;
                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found."));
    }

    @Override
    public void processWebhook(String json, String remoteAddr, String encoding, int rawLen, int bodyLen) throws IOException {
        Appointment appointment = fhirMapper.map(json);

        CreateAppointmentRequest req = new CreateAppointmentRequest();
        req.setOrganizationId(appointment.getOrganizationId());
        req.setScheduledTime(appointment.getScheduledTime());
        req.setPatientId(appointment.getPatientId());
        req.setPatientPhone(appointment.getPatientPhone());
        req.setSubject(appointment.getSubject());
        req.setLocation(appointment.getLocation());
        req.setProvider(appointment.getProvider());
        req.setTimezone(appointment.getTimezone());
        req.setInstructions(appointment.getInstructions());

        Set<ConstraintViolation<CreateAppointmentRequest>> violations = validator.validate(req);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .reduce((a, b) -> a + ", " + b).orElse("Validation failed");
            log.warn("Webhook validation failed: {}", message);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }

        appointment.setTraceContext(currentTraceContext());
        repository.save(appointment);
        apiMetrics.recordAppointmentReceived(appointment.getOrganizationId());
        logWebhookPayload(remoteAddr, encoding, rawLen, bodyLen, json, appointment);
    }

    private Map<String, String> currentTraceContext() {
        Map<String, String> ctx = new HashMap<>();
        W3CTraceContextPropagator.getInstance().inject(Context.current(), ctx, Map::put);
        return ctx;
    }

    private void logWebhookPayload(String remote, String encoding, int rawLen, int bodyLen,
                                   String json, Appointment appointment) {
        String sep = "─".repeat(60);
        StringBuilder sb = new StringBuilder("\n").append("═".repeat(60)).append("\n");
        sb.append("  INCOMING webhook from ").append(remote).append("\n");
        sb.append(sep).append("\n");

        if (encoding != null && encoding.toLowerCase().contains("gzip")) {
            sb.append("  [GZIP] decompressed ").append(rawLen).append(" → ").append(bodyLen).append(" bytes\n");
        } else {
            sb.append("  [BODY] ").append(bodyLen).append(" bytes (uncompressed)\n");
        }

        sb.append("\n  ┌─ Appointment Summary ─────────────────────────\n");
        sb.append("  │ Patient ID: ").append(appointment.getPatientId()).append("\n");
        sb.append("  │ Status:     ").append(appointment.getStatus()).append("\n");
        sb.append("  │ Start:      ").append(appointment.getScheduledTime()).append("\n");
        sb.append("  │ Location:   ").append(appointment.getLocation()).append("\n");
        sb.append("  │ Subject:    ").append(appointment.getSubject()).append("\n");
        sb.append("  └").append("─".repeat(48)).append("\n");

        try {
            Object prettyJson = objectMapper.readValue(json, Object.class);
            sb.append("\n  Full FHIR R4 JSON:\n");
            sb.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(prettyJson));
        } catch (Exception e) {
            sb.append("\n  Raw body:\n").append(json);
        }

        sb.append("\n").append("═".repeat(60));
        log.info(sb.toString());
    }
}
