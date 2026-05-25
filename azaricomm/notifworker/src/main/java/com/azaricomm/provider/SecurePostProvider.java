package com.azaricomm.provider;

import com.azaricomm.model.DeliveryResult;
import com.azaricomm.model.NotificationMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

@Component
public class SecurePostProvider implements MessagingProvider {

    private static final Logger log = LoggerFactory.getLogger(SecurePostProvider.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String clientId;
    private final String clientSecret;
    private final String studentGroup;

    private String cachedToken;
    private Instant tokenExpiry = Instant.EPOCH;

    public SecurePostProvider(RestTemplate restTemplate,
                              @Value("${providers.securepost.url}") String baseUrl,
                              @Value("${providers.securepost.client-id}") String clientId,
                              @Value("${providers.securepost.client-secret}") String clientSecret,
                              @Value("${fakecomworld.student-group}") String studentGroup) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.studentGroup = studentGroup;
    }

    @Override
    public String getName() {
        return "securepost";
    }

    @Override
    public DeliveryResult send(NotificationMessage message) {
        log.info("[SecurePost] Sending to {} | subject: {}", message.getPatientPhone(), message.getSubject());
        try {
            String token = getToken();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-STUDENT-GROUP", studentGroup);
            headers.setBearerAuth(token);

            Map<String, Object> body = Map.of(
                    "format", "SMS",
                    "recipient", message.getPatientPhone(),
                    "body", message.getBody() != null ? message.getBody() : "",
                    "subject", message.getSubject() != null ? message.getSubject() : ""
            );

            ResponseEntity<String> response = restTemplate.postForEntity(
                    baseUrl + "/message", new HttpEntity<>(body, headers), String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                String trackingId = extractTrackingId(response.getBody());
                log.info("[SecurePost] Delivered successfully, trackingId={}", trackingId);
                return DeliveryResult.success(getName(), trackingId);
            }

            log.error("[SecurePost] Unexpected status {}", response.getStatusCode());
            return DeliveryResult.failure(getName(), "Unexpected status: " + response.getStatusCode());

        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 401) {
                synchronized (this) { cachedToken = null; tokenExpiry = Instant.EPOCH; }
            }
            log.error("[SecurePost] HTTP {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return DeliveryResult.failure(getName(), "HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("[SecurePost] Request failed: {}", e.getMessage(), e);
            return DeliveryResult.failure(getName(), "Request failed: " + e.getMessage());
        }
    }

    private synchronized String getToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
            return cachedToken;
        }

        log.debug("[SecurePost] Fetching new JWT token");

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
        authHeaders.set("X-STUDENT-GROUP", studentGroup);

        Map<String, String> authBody = Map.of("clientId", clientId, "clientSecret", clientSecret);

        ResponseEntity<String> tokenResponse = restTemplate.postForEntity(
                baseUrl + "/auth", new HttpEntity<>(authBody, authHeaders), String.class);

        try {
            JsonNode node = objectMapper.readTree(tokenResponse.getBody());
            cachedToken = node.get("accessToken").asText();
            long expiresIn = node.get("expiresIn").asLong(180);
            tokenExpiry = Instant.now().plusSeconds(expiresIn - 30);
            log.debug("[SecurePost] Token acquired, expires in {}s", expiresIn);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse token response", e);
        }

        return cachedToken;
    }

    private String extractTrackingId(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.has("trackingId")) return node.get("trackingId").asText();
        } catch (Exception ignored) {}
        return "SP-unknown";
    }
}
