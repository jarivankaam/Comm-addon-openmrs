package com.azaricomm.provider;

import com.azaricomm.model.DeliveryResult;
import com.azaricomm.model.NotificationMessage;
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
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class LegacyLinkProvider implements MessagingProvider {

    private static final Logger log = LoggerFactory.getLogger(LegacyLinkProvider.class);
    private static final String XML_NS = "http://legacylink.fakecomworld.com/v1";

    private final RestTemplate restTemplate;
    private final String url;
    private final String username;
    private final String password;
    private final String studentGroup;

    public LegacyLinkProvider(RestTemplate restTemplate,
                              @Value("${providers.legacylink.url}") String url,
                              @Value("${providers.legacylink.username}") String username,
                              @Value("${providers.legacylink.password}") String password,
                              @Value("${fakecomworld.student-group}") String studentGroup) {
        this.restTemplate = restTemplate;
        this.url = url;
        this.username = username;
        this.password = password;
        this.studentGroup = studentGroup;
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
            String xmlBody = buildSoapRequest(message);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(xmlBody, headers), String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                String messageRef = extractMessageReference(response.getBody());
                log.info("[LegacyLink] Delivered successfully, messageRef={}", messageRef);
                return DeliveryResult.success(getName(), messageRef);
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
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.set("Accept", "application/xml");
        headers.set("X-STUDENT-GROUP", studentGroup);
        String credentials = Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + credentials);
        return headers;
    }

    private String buildSoapRequest(NotificationMessage message) {
        String phone = escapeXml(message.getPatientPhone() != null ? message.getPatientPhone() : "");
        String text = escapeXml(message.getBody() != null ? message.getBody() : "");
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
               "<SendSmsRequest xmlns=\"" + XML_NS + "\">" +
               "<PhoneNumber>" + phone + "</PhoneNumber>" +
               "<MessageText>" + text + "</MessageText>" +
               "<SenderIdentification>AzariComm</SenderIdentification>" +
               "</SendSmsRequest>";
    }

    private String extractMessageReference(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            NodeList nodes = doc.getElementsByTagNameNS(XML_NS, "MessageReference");
            if (nodes.getLength() > 0) return nodes.item(0).getTextContent();
        } catch (Exception ignored) {}
        return "LL-unknown";
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
