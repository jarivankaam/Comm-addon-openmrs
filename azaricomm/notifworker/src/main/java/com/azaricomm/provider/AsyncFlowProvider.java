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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class AsyncFlowProvider implements MessagingProvider {

    private static final Logger log = LoggerFactory.getLogger(AsyncFlowProvider.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int POLL_INTERVAL_MS = 5000;
    private static final int MAX_POLL_ATTEMPTS = 12; // 60s total

    private final RestTemplate restTemplate;
    private final String url;
    private final String apiKey;
    private final String studentGroup;

    public AsyncFlowProvider(RestTemplate restTemplate,
                             @Value("${providers.asyncflow.url}") String url,
                             @Value("${providers.asyncflow.api-key:}") String apiKey,
                             @Value("${fakecomworld.student-group}") String studentGroup) {
        this.restTemplate = restTemplate;
        this.url = url;
        this.apiKey = apiKey;
        this.studentGroup = studentGroup;
    }

    @Override
    public String getName() {
        return "asyncflow";
    }

    @Override
    public DeliveryResult send(NotificationMessage message) {
        log.info("[AsyncFlow] Submitting for patient {} | destination: {}", message.getPatientId(), message.getPatientPhone());
        try {
            String trackingId = submit(message);
            log.info("[AsyncFlow] Submitted, trackingId={}", trackingId);
            return pollUntilDone(trackingId);
        } catch (HttpStatusCodeException e) {
            log.error("[AsyncFlow] HTTP {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return DeliveryResult.failure(getName(), "HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("[AsyncFlow] Request failed: {}", e.getMessage(), e);
            return DeliveryResult.failure(getName(), "Request failed: " + e.getMessage());
        }
    }

    private String submit(NotificationMessage message) throws Exception {
        HttpHeaders headers = buildHeaders();
        Map<String, Object> body = Map.of(
                "destination", message.getPatientPhone(),
                "content", message.getBody() != null ? message.getBody() : "",
                "priority", "normal"
        );

        ResponseEntity<String> response = restTemplate.postForEntity(
                url, new HttpEntity<>(body, headers), String.class);

        JsonNode node = objectMapper.readTree(response.getBody());
        return node.get("trackingId").asText();
    }

    private DeliveryResult pollUntilDone(String trackingId) throws Exception {
        HttpEntity<Void> request = new HttpEntity<>(buildHeaders());

        for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                Thread.sleep(POLL_INTERVAL_MS);
            }

            ResponseEntity<String> statusResponse = restTemplate.exchange(
                    url + "/" + trackingId, HttpMethod.GET, request, String.class);

            JsonNode node = objectMapper.readTree(statusResponse.getBody());
            String status = node.get("status").asText();
            log.debug("[AsyncFlow] trackingId={} status={}", trackingId, status);

            if ("Completed".equals(status)) {
                log.info("[AsyncFlow] Completed, trackingId={}", trackingId);
                return DeliveryResult.success(getName(), trackingId);
            } else if ("Failed".equals(status)) {
                String error = node.has("errorDetails") && !node.get("errorDetails").isNull()
                        ? node.get("errorDetails").asText() : "Processing failed";
                log.error("[AsyncFlow] Failed, trackingId={}: {}", trackingId, error);
                return DeliveryResult.failure(getName(), error);
            }
        }

        log.error("[AsyncFlow] Timed out waiting for trackingId={}", trackingId);
        return DeliveryResult.failure(getName(), "Timed out waiting for processing to complete");
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-STUDENT-GROUP", studentGroup);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("X-API-KEY", apiKey);
        }
        return headers;
    }
}
