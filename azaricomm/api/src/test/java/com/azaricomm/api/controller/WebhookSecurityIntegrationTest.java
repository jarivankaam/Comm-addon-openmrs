package com.azaricomm.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "OPENMRS_BASE_URL=http://localhost:8080",
        "OPENMRS_USERNAME=test",
        "OPENMRS_PASSWORD=test",
        "WEBHOOK_SECRET=test-secret-12345678901234567890123456789012",
        "CRYPTO_SECRET_KEY=DitIsMijnSuperGeheimeSleutel123!"
})
@AutoConfigureMockMvc
class WebhookSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    private static final String WEBHOOK_PATH = "/api/appointments/webhook/openmrs";
    private static final String HMAC_HEADER_NAME = "X-Webhook-Signature";

    @Test
    void whenWebhookReceivedWithoutSignature_ShouldReturnUnauthorized() throws Exception {
        String payload = "{ \"resourceType\": \"Appointment\" }";

        mockMvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void whenWebhookReceivedWithInvalidSignature_ShouldReturnUnauthorized() throws Exception {
        String payload = "{ \"resourceType\": \"Appointment\" }";

        mockMvc.perform(post(WEBHOOK_PATH)
                        .header(HMAC_HEADER_NAME, "sha256=foutehandtekening123456")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());
    }
}