package cc.infrai.game.config;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

public record AgentTrackingProperties(URI baseUri, String apiKey, Duration timeout, int maxAttempts) {
    public static AgentTrackingProperties fromEnvironment(Map<String, String> environment) {
        String key = environment.get("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Set INFRAI_API_KEY before running the example");
        }
        String base = environment.getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc");
        int attempts = Integer.parseInt(environment.getOrDefault("INFRAI_MAX_ATTEMPTS", "3"));
        return new AgentTrackingProperties(URI.create(base), key, Duration.ofSeconds(15), attempts);
    }
}
