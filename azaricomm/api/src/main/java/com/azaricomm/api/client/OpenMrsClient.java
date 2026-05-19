package com.azaricomm.api.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

@Service
public class OpenMrsClient {

    private static final Logger log = LoggerFactory.getLogger(OpenMrsClient.class);

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;

    @Value("${openmrs.base-url}")
    private String baseUrl;

    @Value("${openmrs.username}")
    private String username;

    @Value("${openmrs.password}")
    private String password;

    public OpenMrsClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode getPatientByIdentifier(String identifier) {
        String url = baseUrl + "/openmrs/ws/rest/v1/patient?identifier=" + identifier + "&v=full";
        log.info("Calling OpenMRS: GET {}", url);
        try {
            String credentials = Base64.getEncoder()
                    .encodeToString((username + ":" + password).getBytes());

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Basic " + credentials)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("OpenMRS returned {} for GET {} — body: {}", response.statusCode(), url, response.body());
                return null;
            }

            JsonNode body = objectMapper.readTree(response.body());
            JsonNode results = body.path("results");
            if (results.isArray() && !results.isEmpty()) {
                JsonNode patient = results.get(0);
                logPatientResponse(identifier, patient);
                return patient;
            }

            log.info("No OpenMRS patient found for identifier {}", identifier);
            return null;

        } catch (Exception e) {
            log.error("Failed to fetch patient {} from OpenMRS: {} - {}", identifier, e.getClass().getSimpleName(), e.getMessage(), e);
            return null;
        }
    }

    public String extractPhone(JsonNode patient) {
        if (patient == null) return null;
        JsonNode attributes = patient.path("person").path("attributes");
        if (!attributes.isArray()) return null;
        for (JsonNode attr : attributes) {
            String type = attr.path("attributeType").path("display").asText("");
            if (type.toLowerCase().contains("phone") || type.toLowerCase().contains("telephone")) {
                return attr.path("value").asText(null);
            }
        }
        return null;
    }

    public String extractDisplayName(JsonNode patient) {
        if (patient == null) return null;
        return patient.path("person").path("preferredName").path("display").asText(null);
    }

    public String extractUuid(JsonNode patient) {
        if (patient == null) return null;
        return patient.path("uuid").asText(null);
    }

    private void logPatientResponse(String identifier, JsonNode patient) {
        String sep = "─".repeat(60);
        StringBuilder sb = new StringBuilder("\n").append(sep).append("\n");
        sb.append("  [OpenMRS] Patient lookup for identifier: ").append(identifier).append("\n");
        sb.append(sep).append("\n");

        sb.append("  UUID:        ").append(patient.path("uuid").asText("n/a")).append("\n");
        sb.append("  Display:     ").append(patient.path("display").asText("n/a")).append("\n");

        JsonNode person = patient.path("person");
        sb.append("  Name:        ").append(person.path("preferredName").path("display").asText("n/a")).append("\n");
        sb.append("  Gender:      ").append(person.path("gender").asText("n/a")).append("\n");
        sb.append("  Birthdate:   ").append(person.path("birthdate").asText("n/a")).append("\n");

        JsonNode attributes = person.path("attributes");
        if (attributes.isArray()) {
            for (JsonNode attr : attributes) {
                String type = attr.path("attributeType").path("display").asText("");
                String value = attr.path("value").asText("n/a");
                sb.append("  ").append(String.format("%-14s", type + ":")).append(" ").append(value).append("\n");
            }
        }

        JsonNode identifiers = patient.path("identifiers");
        if (identifiers.isArray()) {
            for (JsonNode id : identifiers) {
                String type = id.path("identifierType").path("display").asText("");
                String value = id.path("identifier").asText("n/a");
                sb.append("  ").append(String.format("%-14s", type + ":")).append(" ").append(value).append("\n");
            }
        }

        sb.append(sep);
        log.info(sb.toString());
    }
}
