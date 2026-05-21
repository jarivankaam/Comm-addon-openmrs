package com.azaricomm.api.controller;

import com.azaricomm.api.dto.AppointmentCreatedResponse;
import com.azaricomm.api.dto.CreateAppointmentRequest;
import com.azaricomm.api.model.Appointment;
import com.azaricomm.api.service.AppointmentService;
import com.azaricomm.api.webhook.WebhookAuthenticator;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    private static final Logger log = LoggerFactory.getLogger(AppointmentController.class);

    private final AppointmentService appointmentService;
    private final WebhookAuthenticator webhookAuthenticator;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public AppointmentController(AppointmentService appointmentService, WebhookAuthenticator webhookAuthenticator,
                                 ObjectMapper objectMapper, Validator validator) {
        this.appointmentService = appointmentService;
        this.webhookAuthenticator = webhookAuthenticator;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @PostMapping
    public ResponseEntity<AppointmentCreatedResponse> create(
            HttpServletRequest request,
            @RequestHeader(value = "Content-Encoding", required = false) String contentEncoding) throws IOException {

        byte[] rawBytes = request.getInputStream().readAllBytes();
        byte[] bodyBytes = (contentEncoding != null && contentEncoding.toLowerCase().contains("gzip"))
                ? gunzip(rawBytes)
                : rawBytes;

        CreateAppointmentRequest req = objectMapper.readValue(bodyBytes, CreateAppointmentRequest.class);

        var violations = validator.validate(req);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .reduce((a, b) -> a + ", " + b).orElse("Validation failed");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(appointmentService.create(req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Appointment> get(@PathVariable String id) {
        return appointmentService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/cancel")
    public ResponseEntity<Appointment> cancel(@PathVariable String id) {
        return ResponseEntity.ok(appointmentService.cancel(id));
    }

    @PostMapping("/webhook/openmrs")
    public ResponseEntity<String> receiveOpenMrsWebhook(
            HttpServletRequest request,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            @RequestHeader(value = "Content-Encoding", required = false) String contentEncoding) throws IOException {

        log.info("Received OpenMRS webhook from {} (encoding: {}, signature: {})",
                request.getRemoteAddr(), contentEncoding, signature);

        byte[] rawBytes = request.getInputStream().readAllBytes();
        byte[] bodyBytes = (contentEncoding != null && contentEncoding.toLowerCase().contains("gzip"))
                ? gunzip(rawBytes)
                : rawBytes;
        String json = new String(bodyBytes, StandardCharsets.UTF_8);

        if (!webhookAuthenticator.isValid(json, signature)) {
            log.warn("Invalid HMAC signature on OpenMRS webhook");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("{\"error\":\"Invalid signature\"}");
        }

        appointmentService.processWebhook(json, request.getRemoteAddr(), contentEncoding, rawBytes.length, bodyBytes.length);

        return ResponseEntity.ok("{\"status\":\"received\"}");
    }

    @PostMapping("/webhook/debug")
    public ResponseEntity<String> debugWebhook(
            HttpServletRequest request,
            @RequestHeader(value = "Content-Encoding", required = false) String contentEncoding) throws IOException {

        byte[] rawBytes = request.getInputStream().readAllBytes();
        byte[] bodyBytes = (contentEncoding != null && contentEncoding.toLowerCase().contains("gzip"))
                ? gunzip(rawBytes)
                : rawBytes;
        String body = new String(bodyBytes, StandardCharsets.UTF_8);

        StringBuilder sb = new StringBuilder();
        sb.append("=== HEADERS ===\n");
        java.util.Collections.list(request.getHeaderNames())
                .forEach(h -> sb.append(h).append(": ").append(request.getHeader(h)).append("\n"));
        sb.append("\n=== BODY (").append(bodyBytes.length).append(" bytes) ===\n");
        sb.append(body);

        log.info("\n{}", sb);
        return ResponseEntity.ok(sb.toString());
    }

    private static byte[] gunzip(byte[] compressed) throws IOException {
        try (GZIPInputStream gzis = new GZIPInputStream(new java.io.ByteArrayInputStream(compressed));
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int len;
            while ((len = gzis.read(buf)) != -1) baos.write(buf, 0, len);
            return baos.toByteArray();
        }
    }
}
