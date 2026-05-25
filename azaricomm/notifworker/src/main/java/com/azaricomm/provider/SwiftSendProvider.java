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

import java.util.List;
import java.util.Map;

@Component
public class SwiftSendProvider implements MessagingProvider {

    private static final Logger log = LoggerFactory.getLogger(SwiftSendProvider.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final RestTemplate restTemplate;
    private final String url;
    private final String apiKey;
    private final String studentGroup;

    public SwiftSendProvider(RestTemplate restTemplate,
                             @Value("${providers.swiftsend.url}") String url,
                             @Value("${providers.swiftsend.api-key:}") String apiKey,
                             @Value("${fakecomworld.student-group}") String studentGroup) {
        this.restTemplate = restTemplate;
        this.url = url;
        this.apiKey = apiKey;
        this.studentGroup = studentGroup;
    }

    @Override
    public String getName() {
        return "swiftsend";
    }

    @Override
    public DeliveryResult send(NotificationMessage message) {
        log.info("[SwiftSend] Sending to {} | subject: {}", message.getPatientPhone(), message.getSubject());
        try {
            HttpHeaders headers = buildHeaders();
            Map<String, Object> body = Map.of(
                    "type", "SMS",
                    "recipients", List.of(message.getPatientPhone()),
                    "content", message.getBody() != null ? message.getBody() : ""
            );

            ResponseEntity<String> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(body, headers), String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                String messageId = extractMessageId(response.getBody());
                log.info("[SwiftSend] Delivered successfully, messageId={}", messageId);
                return DeliveryResult.success(getName(), messageId);
            }

            log.error("[SwiftSend] Unexpected status {}", response.getStatusCode());
            return DeliveryResult.failure(getName(), "Unexpected status: " + response.getStatusCode());

        } catch (HttpStatusCodeException e) {
            log.error("[SwiftSend] HTTP {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return DeliveryResult.failure(getName(), "HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("[SwiftSend] Request failed: {}", e.getMessage(), e);
            return DeliveryResult.failure(getName(), "Request failed: " + e.getMessage());
        }
    }
//@Override
//public DeliveryResult send(NotificationMessage message) {
//    log.info("[SwiftSend] SIMULATIE: Provider ligt plat!");
//    // We omzeilen de RestTemplate en retourneren direct een foutmelding
//    return DeliveryResult.failure(getName(), "HTTP 503 Service Unavailable (Simulated)");
//}

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-STUDENT-GROUP", studentGroup);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("X-API-KEY", apiKey);
        }
        return headers;
    }

    private String extractMessageId(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.has("messageId")) return node.get("messageId").asText();
        } catch (Exception ignored) {}
        return "SS-unknown";
    }
}
