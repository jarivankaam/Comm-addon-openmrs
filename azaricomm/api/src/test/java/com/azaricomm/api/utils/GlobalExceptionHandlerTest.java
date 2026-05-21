package com.azaricomm.api.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void handleResponseStatusException_ShouldReturnFormattedJson() {
        HttpServletRequest mockRequest = Mockito.mock(HttpServletRequest.class);
        Mockito.when(mockRequest.getRequestURI()).thenReturn("/api/appointments/123");

        ResponseStatusException exception = new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment missing");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleResponseStatusException(exception, mockRequest);
        
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(404, body.get("status"));
        assertEquals("Not Found", body.get("error"));
        assertEquals("/api/appointments/123", body.get("path"));

        Map<String, String> messages = (Map<String, String>) body.get("messages");
        assertEquals("Appointment missing", messages.get("appointment"));
    }
}