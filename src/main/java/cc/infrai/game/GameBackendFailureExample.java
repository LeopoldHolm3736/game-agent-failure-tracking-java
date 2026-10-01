package cc.infrai.game;

import cc.infrai.game.client.InfraiErrorsClient;
import cc.infrai.game.config.AgentTrackingProperties;
import cc.infrai.game.service.GameAgentFailureTracker;
import cc.infrai.game.service.GameAgentFailureTracker.GameAgentContext;

public final class GameBackendFailureExample {
    public static void main(String[] args) throws Exception {
        AgentTrackingProperties properties = AgentTrackingProperties.fromEnvironment(System.getenv());
        InfraiErrorsClient infrai = new InfraiErrorsClient(properties);
        GameAgentFailureTracker tracker = new GameAgentFailureTracker(infrai::capture);

        GameAgentContext moderation = new GameAgentContext(
                "ugc-safety-agent", "classify-player-banner", "asset-banner-2048",
                "spring-tournament", "human-review-priority");
        try {
            tracker.track(moderation, () -> classifyPlayerAsset("unsupported-palette"));
        } catch (IllegalArgumentException expected) {
            System.out.println("Captured asset-banner-2048 under ugc-safety-agent/classify-player-banner");
        }
    }

    private static String classifyPlayerAsset(String palette) {
        if (!"approved-palette".equals(palette)) {
            throw new IllegalArgumentException("asset needs moderation review");
        }
        return "approved";
    }
}
