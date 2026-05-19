package com.azaricomm.api.webhook;

import com.azaricomm.api.model.AppointmentEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final MongoTemplate mongoTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${webhook.secret:}")
    private String secret;

    @Value("${rabbitmq.exchange:openmrs.events}")
    private String exchange;

    @Value("${rabbitmq.routing-key:notification.appointment}")
    private String routingKey;

    public WebhookController(MongoTemplate mongoTemplate, RabbitTemplate rabbitTemplate) {
        this.mongoTemplate = mongoTemplate;
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping
    public ResponseEntity<String> receive(HttpServletRequest request) throws IOException {
        byte[] rawBody = readAllBytes(request.getInputStream());

        String contentEncoding = request.getHeader("Content-Encoding");
        byte[] body;
        if (contentEncoding != null && contentEncoding.toLowerCase().contains("gzip")) {
            body = gunzip(rawBody);
            log.info("GZIP decompressed {} → {} bytes", rawBody.length, body.length);
        } else {
            body = rawBody;
        }

        String fhirJson = new String(body, StandardCharsets.UTF_8);

        verifyHmac(request, fhirJson);

        String appointmentId = extractField(fhirJson, "id");
        String status = extractField(fhirJson, "status");

        AppointmentEvent event = new AppointmentEvent(appointmentId, status, fhirJson);
        mongoTemplate.save(event);
        log.info("Stored appointment {} (status={}) in MongoDB", appointmentId, status);

        rabbitTemplate.convertAndSend(exchange, routingKey, fhirJson);
        log.info("Published appointment {} to RabbitMQ", appointmentId);

        log.info("Webhook received and processed — appointmentId={}", appointmentId);



        return ResponseEntity.ok("{\"status\":\"received\",\"message\":\"Webhook processed\"}");
    }

    private void verifyHmac(HttpServletRequest request, String body) {
        if (secret == null || secret.isBlank()) return;
        String signature = request.getHeader("X-Webhook-Signature");
        String expected = "sha256=" + hmacSha256(body, secret);
        if (expected.equals(signature)) {
            log.info("HMAC signature valid");
        } else {
            log.warn("HMAC signature invalid — expected={} received={}", expected, signature);
        }
    }

    private String extractField(String json, String field) {
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode value = node.get(field);
            return value != null ? value.asText() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
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

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        while ((len = is.read(buf)) != -1) baos.write(buf, 0, len);
        return baos.toByteArray();
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
            log.error("HMAC computation failed", e);
            return "";
        }
    }
}
