package com.azaricomm.api.controller;

import com.azaricomm.api.dto.AppointmentCreatedResponse;
import com.azaricomm.api.dto.CreateAppointmentRequest;
import com.azaricomm.api.model.Appointment;
import com.azaricomm.api.service.AppointmentService;
import com.azaricomm.api.webhook.WebhookAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    public AppointmentController(AppointmentService appointmentService, WebhookAuthenticator webhookAuthenticator) {
        this.appointmentService = appointmentService;
        this.webhookAuthenticator = webhookAuthenticator;
    }

    @PostMapping
    public ResponseEntity<AppointmentCreatedResponse> create(@Valid @RequestBody CreateAppointmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appointmentService.create(request));
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
