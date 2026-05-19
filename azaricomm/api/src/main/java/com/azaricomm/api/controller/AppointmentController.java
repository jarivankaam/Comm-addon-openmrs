package com.azaricomm.api.controller;

import com.azaricomm.api.client.OpenMrsClient;
import com.azaricomm.api.dto.CreateAppointmentRequest;
import com.azaricomm.api.model.Appointment;
import com.azaricomm.api.model.AppointmentStatus;
import com.azaricomm.api.repository.AppointmentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.zip.GZIPInputStream;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private static final Logger log = LoggerFactory.getLogger(AppointmentController.class);

    private final AppointmentRepository repository;
    private final ObjectMapper objectMapper;
    private final OpenMrsClient openMrsClient;

    @Value("${webhook.secret:}")
    private String webhookSecret;

    public AppointmentController(AppointmentRepository repository, ObjectMapper objectMapper,
                                 OpenMrsClient openMrsClient) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.openMrsClient = openMrsClient;
    }

    @PostMapping
    public ResponseEntity<Appointment> create(@Valid @RequestBody CreateAppointmentRequest request) {
        Appointment appointment = new Appointment();

        // Map DTO to the DB entity
        appointment.setOrganizationId(request.getOrganizationId());
        appointment.setScheduledTime(request.getScheduledTime());
        appointment.setPatientId(request.getPatientId());
        appointment.setPatientPhone(request.getPatientPhone());
        appointment.setSubject(request.getSubject());
        appointment.setLocation(request.getLocation());
        appointment.setInstructions(request.getInstructions());
        appointment.setProvider(request.getProvider());
        appointment.setTimezone(request.getTimezone());

        // Forced backend logica
        appointment.setStatus(AppointmentStatus.SCHEDULED);
        appointment.setCreatedAt(Instant.now());

        // Create TTL-index of 14 days after scheduledTime.
        appointment.setExpireAt(request.getScheduledTime().plus(14, ChronoUnit.DAYS));

        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(appointment));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Appointment> get(@PathVariable String id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/cancel")
    public ResponseEntity<Appointment> cancel(@PathVariable String id) {
        return repository.findById(id)
                .map(a -> {
                    // Check if appointment isnt already cancelled.
                    if (a.getStatus() == AppointmentStatus.CANCELLED) {
                        throw new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "This appointment is already cancelled."
                        );
                    }

                    a.setStatus(AppointmentStatus.CANCELLED);
                    a.setExpireAt(Instant.now().plus(14, ChronoUnit.DAYS)); // 14 days from now.
                    return ResponseEntity.ok(repository.save(a));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/webhook/openmrs")
    public ResponseEntity<String> receiveOpenMrsWebhook(
            HttpServletRequest request,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @RequestHeader(value = "Content-Encoding", required = false) String contentEncoding) throws IOException {

        byte[] rawBytes = request.getInputStream().readAllBytes();

        byte[] bodyBytes = (contentEncoding != null && contentEncoding.toLowerCase().contains("gzip"))
                ? gunzip(rawBytes)
                : rawBytes;

        String json = new String(bodyBytes, StandardCharsets.UTF_8);

        if (!webhookSecret.isEmpty()) {
            String expected = "sha256=" + hmacSha256(json, webhookSecret);
            if (!expected.equals(signature)) {
                log.warn("Invalid HMAC signature on OpenMRS webhook");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("{\"error\":\"Invalid signature\"}");
            }
        }

        Appointment appointment = mapFhirToAppointment(json);
        repository.save(appointment);
        logWebhookPayload(request.getRemoteAddr(), contentEncoding, rawBytes.length, bodyBytes.length, json, appointment);

        return ResponseEntity.ok("{\"status\":\"received\"}");
    }

    private Appointment mapFhirToAppointment(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);

        Appointment appointment = new Appointment();
        appointment.setCreatedAt(Instant.now());

        appointment.setPatientId(root.path("id").asText(null));
        String fhirStartText = root.path("start").asText(null);
        if (fhirStartText != null && !fhirStartText.isBlank()) {
            try {
                java.time.OffsetDateTime odt = java.time.OffsetDateTime.parse(fhirStartText);
                Instant parsedTime = odt.toInstant();

                appointment.setScheduledTime(parsedTime);
                appointment.setExpireAt(parsedTime.plus(14, ChronoUnit.DAYS));
            } catch (Exception e) {
                log.error("Failed to parse OpenMRS scheduledTime string: " + fhirStartText, e);
                Instant fallback = Instant.now();
                appointment.setScheduledTime(fallback);
                appointment.setExpireAt(fallback.plus(14, ChronoUnit.DAYS));
            }
        }
        appointment.setStatus(mapFhirStatus(root.path("status").asText("booked")));

        JsonNode participants = root.path("participant");
        if (participants.isArray()) {
            for (JsonNode participant : participants) {
                JsonNode actor = participant.path("actor");
                String type = actor.path("type").asText("");
                String display = actor.path("display").asText("");
                String reference = actor.path("reference").asText("");

                if ("Patient".equalsIgnoreCase(type)) {
                    String patientRef = reference.contains("/")
                            ? reference.substring(reference.lastIndexOf('/') + 1)
                            : reference;
                    appointment.setPatientId(patientRef);

                    String identifier = actor.path("identifier").path("value").asText(null);
                    if (identifier == null) identifier = patientRef;

                    JsonNode patient = openMrsClient.getPatientByIdentifier(identifier);
                    String name = openMrsClient.extractDisplayName(patient);
                    String phone = openMrsClient.extractPhone(patient);
                    String uuid = openMrsClient.extractUuid(patient);

                    if (name == null) name = display;
                    if (uuid != null) appointment.setPatientId(uuid);
                    appointment.setPatientPhone(phone);
                    appointment.setSubject("Afspraakherinnering voor " + name);
                }

                if ("Location".equalsIgnoreCase(type) || "HealthcareService".equalsIgnoreCase(type)) {
                    appointment.setLocation(display);
                }
            }
        }

        return appointment;
    }

    private static AppointmentStatus mapFhirStatus(String fhirStatus) {
        return switch (fhirStatus.toLowerCase()) {
            case "booked", "pending" -> AppointmentStatus.SCHEDULED;
            case "cancelled", "noshow" -> AppointmentStatus.CANCELLED;
            case "fulfilled" -> AppointmentStatus.SENT;
            default -> AppointmentStatus.SCHEDULED;
        };
    }

    private static byte[] gunzip(byte[] compressed) throws IOException {
        try (GZIPInputStream gzis = new GZIPInputStream(
                new java.io.ByteArrayInputStream(compressed));
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = gzis.read(buf)) != -1) {
                baos.write(buf, 0, len);
            }
            return baos.toByteArray();
        }
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

    private static String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC error", e);
        }
    }
}