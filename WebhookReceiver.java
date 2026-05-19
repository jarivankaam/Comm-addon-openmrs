import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.GZIPInputStream;

/**
 * Appointment FHIR Webhook Test Receiver
 * ========================================
 * A standalone Java HTTP server (no dependencies beyond JDK) that
 * receives, decompresses, verifies, and pretty-prints incoming
 * FHIR R4 Appointment webhooks from the OpenMRS module.
 *
 * Usage:
 *     javac WebhookReceiver.java
 *     java WebhookReceiver
 *
 *     With HMAC secret:
 *     java WebhookReceiver --secret your-secret-here
 *
 *     Custom port:
 *     java WebhookReceiver --port 8090 --secret mysecret
 *
 *     Then set in OpenMRS → Settings:
 *     appointmentwebhook.endpoint.url = http://<your-ip>:5000/webhook
 */
public class WebhookReceiver {

    private static int PORT = 5000;
    private static String SECRET = "";

    // ANSI colors
    private static final String GREEN  = "\u001B[92m";
    private static final String YELLOW = "\u001B[93m";
    private static final String RED    = "\u001B[91m";
    private static final String CYAN   = "\u001B[96m";
    private static final String BOLD   = "\u001B[1m";
    private static final String RESET  = "\u001B[0m";
    private static final String LINE   = "═".repeat(60);
    private static final String DASH   = "─".repeat(60);

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static void main(String[] args) throws Exception {
        // Parse CLI args
        for (int i = 0; i < args.length; i++) {
            if ("--port".equals(args[i]) && i + 1 < args.length) {
                PORT = Integer.parseInt(args[++i]);
            } else if ("--secret".equals(args[i]) && i + 1 < args.length) {
                SECRET = args[++i];
            }
        }

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
        server.createContext("/webhook", new WebhookHandler());
        server.createContext("/health", new HealthHandler());
        server.setExecutor(null);

        printBanner();
        server.start();
    }

    // ═══════════════════════════════════════════════════════════════
    // Webhook Handler
    // ═══════════════════════════════════════════════════════════════
    static class WebhookHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String time = LocalDateTime.now().format(TIME_FMT);
            String remote = exchange.getRemoteAddress().getAddress().getHostAddress();
            log(GREEN, "INCOMING", "POST /webhook from " + remote);
            System.out.println(DASH);

            // ── 1. Print headers ──────────────────────────────────
            log(YELLOW, "HEADERS", "");
            exchange.getRequestHeaders().forEach((key, values) -> {
                String k = key.toLowerCase();
                if (k.startsWith("content-") || k.startsWith("x-") ||
                    k.equals("user-agent") || k.equals("accept")) {
                    System.out.println("  " + BOLD + key + RESET + ": " + String.join(", ", values));
                }
            });

            // ── 2. Read and decompress body ───────────────────────
            byte[] rawBody = readAllBytes(exchange.getRequestBody());
            String contentEncoding = getHeader(exchange, "Content-Encoding");
            byte[] body;

            if (contentEncoding.toLowerCase().contains("gzip")) {
                try {
                    body = gunzip(rawBody);
                    log(GREEN, "GZIP", "Decompressed " + rawBody.length + " → " + body.length + " bytes");
                } catch (Exception e) {
                    log(RED, "GZIP ERROR", e.getMessage());
                    body = rawBody;
                }
            } else {
                body = rawBody;
                log(YELLOW, "BODY", "Uncompressed, " + body.length + " bytes");
            }

            String json = new String(body, StandardCharsets.UTF_8);

            // ── 3. Verify HMAC signature ──────────────────────────
            String signatureHeader = getHeader(exchange, "X-Webhook-Signature");
            if (!SECRET.isEmpty()) {
                String expected = "sha256=" + hmacSha256(json, SECRET);
                if (expected.equals(signatureHeader)) {
                    log(GREEN, "HMAC", "Signature VALID \u2713");
                } else {
                    log(RED, "HMAC", "Signature INVALID \u2717");
                    log(RED, "  Expected", expected);
                    log(RED, "  Received", signatureHeader);
                }
            } else if (!signatureHeader.isEmpty()) {
                log(YELLOW, "HMAC", "Signature present but no --secret configured");
            } else {
                log(YELLOW, "HMAC", "No signature (no secret on either side)");
            }

            // ── 4. Parse and display ──────────────────────────────
            System.out.println();
            printAppointmentSummary(json);
            System.out.println();
            System.out.println(BOLD + "  Full FHIR R4 JSON:" + RESET);
            System.out.println(prettyPrintJson(json));
            System.out.println();
            System.out.println(LINE);
            System.out.println();

            sendResponse(exchange, 200, "{\"status\":\"received\",\"message\":\"Webhook processed\"}");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Health Handler
    // ═══════════════════════════════════════════════════════════════
    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            sendResponse(exchange, 200, "{\"status\":\"ok\",\"service\":\"webhook-test-receiver\"}");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // FHIR JSON Summary Printer
    // ═══════════════════════════════════════════════════════════════
    private static void printAppointmentSummary(String json) {
        System.out.println(BOLD + "  ┌─ Appointment Summary ─────────────────────────" + RESET);
        System.out.println("  │ ID:       " + extractValue(json, "id"));
        System.out.println("  │ Status:   " + extractValue(json, "status"));
        System.out.println("  │ Start:    " + extractValue(json, "start"));
        System.out.println("  │ End:      " + extractValue(json, "end"));
        System.out.println("  │ Duration: " + extractValue(json, "minutesDuration") + " min");

        // Extract participants
        int idx = 0;
        while (true) {
            int participantStart = json.indexOf("\"participant\"", idx);
            if (participantStart < 0) break;

            // Find all actor blocks
            int searchFrom = participantStart;
            while (true) {
                int actorPos = json.indexOf("\"actor\"", searchFrom);
                if (actorPos < 0) break;

                String type = extractValueAfter(json, "\"type\"", actorPos);
                String display = extractValueAfter(json, "\"display\"", actorPos);
                String status = extractValueAfter(json, "\"status\"", actorPos);

                if (!type.isEmpty() && !display.isEmpty()) {
                    System.out.printf("  │ %-14s %s (%s)%n", type, display, status);
                }

                // Move past this actor block
                searchFrom = actorPos + 50;
                // Stop if we've gone past the participant array
                int nextBracket = json.indexOf("]", participantStart + 15);
                if (nextBracket > 0 && searchFrom > nextBracket) break;
            }
            break; // Only one participant array
        }

        System.out.println("  └" + "─".repeat(48));
    }

    // ═══════════════════════════════════════════════════════════════
    // Utility Methods
    // ═══════════════════════════════════════════════════════════════

    private static String extractValue(String json, String key) {
        return extractValueAfter(json, "\"" + key + "\"", 0);
    }

    private static String extractValueAfter(String json, String key, int fromIndex) {
        int keyPos = json.indexOf(key, fromIndex);
        if (keyPos < 0) return "n/a";

        int colonPos = json.indexOf(":", keyPos + key.length());
        if (colonPos < 0) return "n/a";

        // Skip whitespace after colon
        int valStart = colonPos + 1;
        while (valStart < json.length() && json.charAt(valStart) == ' ') valStart++;

        if (valStart >= json.length()) return "n/a";

        char first = json.charAt(valStart);
        if (first == '"') {
            // String value
            int valEnd = json.indexOf('"', valStart + 1);
            if (valEnd < 0) return "n/a";
            return json.substring(valStart + 1, valEnd);
        } else if (first == '{' || first == '[') {
            return "(object)";
        } else {
            // Number, boolean, null
            int valEnd = valStart;
            while (valEnd < json.length() && json.charAt(valEnd) != ',' &&
                   json.charAt(valEnd) != '}' && json.charAt(valEnd) != ']') {
                valEnd++;
            }
            return json.substring(valStart, valEnd).trim();
        }
    }

    private static String prettyPrintJson(String json) {
        StringBuilder sb = new StringBuilder();
        int indent = 0;
        boolean inString = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
                sb.append(c);
                continue;
            }

            if (inString) {
                sb.append(c);
                continue;
            }

            switch (c) {
                case '{': case '[':
                    sb.append(c).append('\n');
                    indent++;
                    sb.append("  ".repeat(indent));
                    break;
                case '}': case ']':
                    sb.append('\n');
                    indent--;
                    sb.append("  ".repeat(indent)).append(c);
                    break;
                case ',':
                    sb.append(c).append('\n').append("  ".repeat(indent));
                    break;
                case ':':
                    sb.append(": ");
                    break;
                default:
                    if (!Character.isWhitespace(c)) sb.append(c);
            }
        }
        return sb.toString();
    }

    private static byte[] gunzip(byte[] compressed) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
        GZIPInputStream gzis = new GZIPInputStream(bais);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        while ((len = gzis.read(buf)) != -1) {
            baos.write(buf, 0, len);
        }
        gzis.close();
        return baos.toByteArray();
    }

    private static String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return "error:" + e.getMessage();
        }
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int len;
        while ((len = is.read(buf)) != -1) {
            baos.write(buf, 0, len);
        }
        return baos.toByteArray();
    }

    private static String getHeader(HttpExchange exchange, String name) {
        var values = exchange.getRequestHeaders().get(name);
        if (values != null && !values.isEmpty()) return values.get(0);
        // Try case-insensitive
        for (var entry : exchange.getRequestHeaders().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name) && !entry.getValue().isEmpty()) {
                return entry.getValue().get(0);
            }
        }
        return "";
    }

    private static void sendResponse(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void log(String color, String label, String message) {
        String time = LocalDateTime.now().format(TIME_FMT);
        System.out.println(CYAN + time + RESET + " " + color + BOLD + "[" + label + "]" + RESET + " " + message);
    }

    private static void printBanner() {
        String secretDisplay = SECRET.isEmpty()
                ? YELLOW + "not set" + RESET
                : GREEN + SECRET.substring(0, Math.min(4, SECRET.length())) + "****" + RESET;

        System.out.println("\n" + BOLD + LINE);
        System.out.println("  \uD83C\uDFE5  Appointment FHIR Webhook Test Receiver");
        System.out.println(LINE + RESET + "\n");
        System.out.println("  Listening on:    " + GREEN + "http://0.0.0.0:" + PORT + "/webhook" + RESET);
        System.out.println("  HMAC Secret:     " + secretDisplay);
        System.out.println();
        System.out.println("  Set in OpenMRS \u2192 Settings:");
        System.out.println("    appointmentwebhook.endpoint.url = " + CYAN + "http://<your-ip>:" + PORT + "/webhook" + RESET);
        System.out.println();
        System.out.println(BOLD + DASH + RESET);
        System.out.println("  Wachten op webhooks...");
        System.out.println(BOLD + DASH + RESET + "\n");
    }
}
