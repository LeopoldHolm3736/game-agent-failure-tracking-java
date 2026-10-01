package cc.infrai.game.client;

import cc.infrai.game.config.AgentTrackingProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class InfraiErrorsClient {
    private final AgentTrackingProperties properties;
    private final HttpClient http;

    public InfraiErrorsClient(AgentTrackingProperties properties) {
        this(properties, HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
    }

    InfraiErrorsClient(AgentTrackingProperties properties, HttpClient http) {
        this.properties = properties;
        this.http = http;
    }

    public void capture(CapturedFailure failure) {
        String payload = "{" +
                "\"title\":" + quote(failure.title()) + "," +
                "\"message\":" + quote(failure.message()) + "," +
                "\"level\":\"error\"," +
                "\"fingerprint\":[" + quote(failure.agent()) + "," + quote(failure.step()) + "]," +
                "\"exception\":" + quote(failure.exception()) + "," +
                "\"context\":{" +
                "\"asset_id\":" + quote(failure.assetId()) + "," +
                "\"live_event_id\":" + quote(failure.liveEventId()) + "," +
                "\"moderation_queue\":" + quote(failure.moderationQueue()) + "}}";
        sendWithBackoff("POST", "/v1/errors/capture", payload, UUID.randomUUID().toString());
    }

    private void sendWithBackoff(String method, String path, String payload, String idempotencyKey) {
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            HttpRequest request = HttpRequest.newBuilder(resolve(path))
                    .timeout(properties.timeout())
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .method(method, HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                Envelope envelope = Envelope.decode(response.body());
                if (response.statusCode() == 429 && attempt < properties.maxAttempts()) {
                    pause(retryDelay(response, attempt));
                    continue;
                }
                if (!envelope.ok()) {
                    throw new InfraiApiException(envelope.errorCode(), envelope.errorMessage(), response.statusCode());
                }
                if (response.statusCode() >= 500) {
                    throw new IllegalStateException("Infrai transport response: HTTP " + response.statusCode());
                }
                return;
            } catch (IOException e) {
                throw new IllegalStateException("Could not reach Infrai", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while calling Infrai", e);
            }
        }
        throw new IllegalStateException("Retry budget exhausted");
    }

    private URI resolve(String path) { return properties.baseUri().resolve(path); }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        List<String> values = response.headers().allValues("Retry-After");
        if (!values.isEmpty()) {
            try { return Duration.ofSeconds(Long.parseLong(values.get(0))); }
            catch (NumberFormatException ignored) { }
        }
        return Duration.ofMillis(250L * (1L << (attempt - 1)));
    }

    private static void pause(Duration delay) throws InterruptedException {
        Thread.sleep(delay.toMillis());
    }

    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c < 0x20 ? String.format(Locale.ROOT, "\\u%04x", (int) c) : c);
            }
        }
        return out.append('\"').toString();
    }

    private record Envelope(boolean ok, String errorCode, String errorMessage) {
        static Envelope decode(String json) {
            boolean ok = json.matches("(?s).*\\\"ok\\\"\\s*:\\s*true.*");
            if (ok) return new Envelope(true, "", "");
            return new Envelope(false, stringField(json, "code"), stringField(json, "message"));
        }

        private static String stringField(String json, String field) {
            String marker = "\"" + field + "\"";
            int key = json.indexOf(marker);
            if (key < 0) return "UNKNOWN";
            int colon = json.indexOf(':', key + marker.length());
            int start = json.indexOf('\"', colon + 1);
            int end = start < 0 ? -1 : json.indexOf('\"', start + 1);
            return start >= 0 && end > start ? json.substring(start + 1, end) : "UNKNOWN";
        }
    }

    public record CapturedFailure(String title, String message, String agent, String step,
                                  String exception, String assetId, String liveEventId,
                                  String moderationQueue) { }
}
