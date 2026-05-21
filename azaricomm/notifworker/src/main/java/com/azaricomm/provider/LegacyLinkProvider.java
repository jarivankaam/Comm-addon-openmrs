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

@Component
public class LegacyLinkProvider implements MessagingProvider {

    private static final Logger log = LoggerFactory.getLogger(LegacyLinkProvider.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final RestTemplate restTemplate;
    private final String url;
    private final String apiKey;

    public LegacyLinkProvider(RestTemplate restTemplate,
                              @Value("${providers.legacylink.url}") String url,
                              @Value("${providers.legacylink.api-key:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.url = url;
        this.apiKey = apiKey;
    }

    @Override
    public String getName() {
        return "legacylink";
    }

    @Override
    public DeliveryResult send(NotificationMessage message) {
        log.info("[LegacyLink] Sending to {} | subject: {}", message.getPatientPhone(), message.getSubject());
        try {
            HttpHeaders headers = buildHeaders();
            ResponseEntity<String> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(message, headers), String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                String messageId = extractMessageId(response.getBody());
                log.info("[LegacyLink] Delivered successfully, messageId={}", messageId);
                return DeliveryResult.success(getName(), messageId);
            }

            log.error("[LegacyLink] Unexpected status {}", response.getStatusCode());
            return DeliveryResult.failure(getName(), "Unexpected status: " + response.getStatusCode());

        } catch (HttpStatusCodeException e) {
            log.error("[LegacyLink] HTTP {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            return DeliveryResult.failure(getName(), "HTTP " + e.getStatusCode() + ": " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("[LegacyLink] Request failed: {}", e.getMessage(), e);
            return DeliveryResult.failure(getName(), "Request failed: " + e.getMessage());
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("X-Api-Key", apiKey);
        }
        return headers;
    }

    private String extractMessageId(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.has("messageId")) return node.get("messageId").asText();
        } catch (Exception ignored) {}
        return "LL-unknown";
    }
}
