package com.azaricomm.api.utils;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private HttpServletRequest mockRequest;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        mockRequest = Mockito.mock(HttpServletRequest.class);
        when(mockRequest.getRequestURI()).thenReturn("/api/appointments/123");
    }

    @Test
    void handleResponseStatusException_ShouldReturnFormattedJson() {
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment missing");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleResponseStatusException(exception, mockRequest);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);

        assertEquals(404, body.get("status"));
        assertEquals("Not Found", body.get("error"));
        assertEquals("/api/appointments/123", body.get("path"));
        assertNotNull(body.get("timestamp"));
        assertTrue(body.get("timestamp") instanceof Instant || body.get("timestamp") instanceof String);
    }

    @Test
    void handleResponseStatusException_WhenReasonIsNull_ShouldNotCrash() {
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleResponseStatusException(exception, mockRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(400, body.get("status"));
    }

    @Test
    void handleIllegalArgumentException_ShouldReturn400BadRequest_WithCustomMessage() {
        IllegalArgumentException exception = new IllegalArgumentException("Invalid date format received from OpenMRS: null");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleIllegalArgumentException(exception, mockRequest);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);

        assertEquals(400, body.get("status"));
        assertEquals("Bad Request", body.get("error"));
        assertEquals("Invalid date format received from OpenMRS: null", body.get("message"));
        assertEquals("/api/appointments/123", body.get("path"));
    }

    @Test
    void handleGenericException_ShouldReturn500InternalServerError_WithoutLeakingDetails() {
        Exception exception = new NullPointerException("Database connection lost at row 452");

        ResponseEntity<Map<String, Object>> response = exceptionHandler.handleAllExceptions(exception, mockRequest);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);

        assertEquals(500, body.get("status"));
        assertEquals("Internal Server Error", body.get("error"));
        
        assertNotNull(body.get("message"));
    }
}