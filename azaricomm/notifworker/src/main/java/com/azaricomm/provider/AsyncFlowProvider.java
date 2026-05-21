package com.azaricomm.provider;

import com.azaricomm.model.DeliveryResult;
import com.azaricomm.model.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class AsyncFlowProvider implements MessagingProvider {

    private static final Logger log = LoggerFactory.getLogger(AsyncFlowProvider.class);

    private final RestTemplate restTemplate;
    private final String url;
    private final String apiKey;

    public AsyncFlowProvider(RestTemplate restTemplate,
                             @Value("${providers.asyncflow.url}") String url,
                             @Value("${providers.asyncflow.api-key:}") String apiKey) {
        this.restTemplate = restTemplate;
        this.url = url;
        this.apiKey = apiKey;
    }

    @Override
    public String getName() {
        return "asyncflow";
    }

    @Override
    public DeliveryResult send(NotificationMessage message) {
        String jobId = "AF-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("[AsyncFlow] Firing async POST for patient {} | jobId={}", message.getPatientId(), jobId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set("X-Api-Key", apiKey);
        }
        HttpEntity<NotificationMessage> request = new HttpEntity<>(message, headers);

        // Fire and forget — delivery is async on their side
        CompletableFuture.runAsync(() -> {
            try {
                restTemplate.postForEntity(url, request, String.class);
                log.info("[AsyncFlow] POST accepted by provider for jobId={}", jobId);
            } catch (Exception e) {
                log.warn("[AsyncFlow] POST failed for jobId={}: {}", jobId, e.getMessage());
            }
        });

        return DeliveryResult.success(getName(), jobId);
    }
}
