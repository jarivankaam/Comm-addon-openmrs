package com.azaricomm.api.controller;

import com.azaricomm.api.repository.ProviderRepository;
import com.azaricomm.api.service.AppointmentService;
import com.azaricomm.api.webhook.WebhookAuthenticator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AppointmentController.class)
class AppointmentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AppointmentService appointmentService;

    @MockBean
    private ProviderRepository providerRepository;

    @MockBean
    private WebhookAuthenticator webhookAuthenticator;

    private Map<String, Object> createValidBaseRequest() {
        Map<String, Object> request = new HashMap<>();
        request.put("provider", "active-provider-123");
        request.put("timezone", "+01:00");
        request.put("patientId", "patient-789");
        request.put("patientPhone", "+31612345678");
        request.put("location", "Room 4a");
        request.put("scheduledTime", Instant.now().plus(5, ChronoUnit.DAYS));
        request.put("organizationId", "org-456");
        request.put("subject", "Regular checkup");
        return request;
    }

    @Test
    void whenPostInvalidAppointmentData_ShouldReturn400BadRequestFromHandler() throws Exception {
        Map<String, Object> invalidRequest = createValidBaseRequest();
        invalidRequest.put("provider", "unknown-provider-id");
        invalidRequest.put("timezone", "Europa/NietBestaand");

        when(providerRepository.existsByIdAndIsActiveTrue("unknown-provider-id")).thenReturn(false);

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/appointments"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.messages.timezone", containsString("Invalid time zone")))
                .andExpect(jsonPath("$.messages.provider", containsString("Provider is invalid")));
    }

    @Test
    void whenPostValidAppointmentData_ShouldPassValidationAndReturn2xx() throws Exception {
        Map<String, Object> validRequest = createValidBaseRequest();

        when(providerRepository.existsByIdAndIsActiveTrue("active-provider-123")).thenReturn(true);

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated());
    }

    @Test
    void whenInternalServerErrorOccurs_ShouldReturn500MaskedByGlobalHandler() throws Exception {
        Map<String, Object> request = createValidBaseRequest();

        when(providerRepository.existsByIdAndIsActiveTrue(Mockito.anyString()))
                .thenThrow(new RuntimeException("MongoDB connection timeout failure!"));

        mockMvc.perform(post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred on the server."));
    }
}