package org.openmrs.module.appointmentwebhook.api;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Context;
import org.openmrs.module.appointments.model.Appointment;
import org.openmrs.module.appointmentwebhook.fhir.FhirAppointmentMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;

/**
 * Sends FHIR R4 Appointment JSON to a configured webhook endpoint.
 *
 * <p>Configuration via OpenMRS Global Properties:</p>
 * <ul>
 *   <li>{@code appointmentwebhook.endpoint.url} — Target URL (required)</li>
 *   <li>{@code appointmentwebhook.endpoint.secret} — HMAC-SHA256 secret (optional)</li>
 *   <li>{@code appointmentwebhook.endpoint.timeout} — Timeout ms (default 10000)</li>
 *   <li>{@code appointmentwebhook.enabled} — Master switch (default true)</li>
 *   <li>{@code appointmentwebhook.fhir.serverBase} — FHIR base URL for refs (optional)</li>
 * </ul>
 */
public class WebhookService {

    private static final Log log = LogFactory.getLog(WebhookService.class);

    public static final String GP_WEBHOOK_URL      = "appointmentwebhook.endpoint.url";
    public static final String GP_WEBHOOK_SECRET   = "appointmentwebhook.endpoint.secret";
    public static final String GP_WEBHOOK_TIMEOUT  = "appointmentwebhook.endpoint.timeout";
    public static final String GP_WEBHOOK_ENABLED  = "appointmentwebhook.enabled";
    public static final String GP_FHIR_SERVER_BASE = "appointmentwebhook.fhir.serverBase";
    public static final String GP_MESSAGE_PROVIDER = "appointmentwebhook.messageProvider";

    private static final int DEFAULT_TIMEOUT_MS = 10_000;
    private static final String FHIR_CONTENT_TYPE = "application/fhir+json; charset=UTF-8";

    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    public void sendAppointmentAsync(Appointment appointment) {
        if (!isEnabled()) {
            log.debug("Webhook disabled — skipping.");
            return;
        }

        String webhookUrl = getWebhookUrl();
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            log.warn("Webhook URL not configured. Set: " + GP_WEBHOOK_URL);
            return;
        }

        // Read ALL config on the calling thread (has OpenMRS context)
        String messageProvider = getMessageProvider();
        String fhirJson = FhirAppointmentMapper.toFhirJson(appointment, getServerBase(), messageProvider);
        String secret = getWebhookSecret();
        int timeout = getTimeout();

        // Async thread — no Context calls allowed here
        executor.submit(() -> {
            try {
                int status = sendPost(webhookUrl, fhirJson, secret, timeout);
                log.info("Webhook sent for Appointment/" + appointment.getUuid()
                        + " → HTTP " + status
                        + " (" + fhirJson.length() + " chars)");
            } catch (Exception e) {
                log.error("Webhook failed for Appointment/" + appointment.getUuid(), e);
            }
        });
    }

    private int sendPost(String webhookUrl, String fhirJson, String secret, int timeout) throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(webhookUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);

            conn.setRequestProperty("Content-Type", FHIR_CONTENT_TYPE);
            conn.setRequestProperty("Accept", "application/fhir+json");
            conn.setRequestProperty("User-Agent", "OpenMRS-AppointmentWebhook/1.0");
            conn.setRequestProperty("X-FHIR-Version", "4.0.1");
            conn.setRequestProperty("Content-Encoding", "gzip");

            if (secret != null && !secret.trim().isEmpty()) {
                String sig = hmacSha256(fhirJson, secret);
                conn.setRequestProperty("X-Webhook-Signature", "sha256=" + sig);
            }

            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);

            byte[] compressed = gzip(fhirJson.getBytes(StandardCharsets.UTF_8));
            conn.setRequestProperty("Content-Length", String.valueOf(compressed.length));

            try (OutputStream os = conn.getOutputStream()) {
                os.write(compressed);
            }

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                log.error("Webhook returned HTTP " + code + " for: " + webhookUrl);
            }
            return code;

        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length / 2);
        try (GZIPOutputStream gz = new GZIPOutputStream(baos)) {
            gz.write(data);
        }
        return baos.toByteArray();
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            log.error("HMAC computation failed", e);
            return "";
        }
    }

    private boolean isEnabled() {
        return "true".equalsIgnoreCase(
                Context.getAdministrationService()
                        .getGlobalProperty(GP_WEBHOOK_ENABLED, "true").trim());
    }

    private String getWebhookUrl() {
        return Context.getAdministrationService()
                .getGlobalProperty(GP_WEBHOOK_URL, "");
    }

    private String getWebhookSecret() {
        return Context.getAdministrationService()
                .getGlobalProperty(GP_WEBHOOK_SECRET, "");
    }

    private String getServerBase() {
        return Context.getAdministrationService()
                .getGlobalProperty(GP_FHIR_SERVER_BASE, "");
    }

    private String getMessageProvider() {
        return Context.getAdministrationService()
                .getGlobalProperty(GP_MESSAGE_PROVIDER, "");
    }

    private int getTimeout() {
        try {
            return Integer.parseInt(Context.getAdministrationService()
                    .getGlobalProperty(GP_WEBHOOK_TIMEOUT,
                            String.valueOf(DEFAULT_TIMEOUT_MS)).trim());
        } catch (NumberFormatException e) {
            return DEFAULT_TIMEOUT_MS;
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}
