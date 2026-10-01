package cc.infrai.game.service;

import cc.infrai.game.client.InfraiErrorsClient.CapturedFailure;
import cc.infrai.game.service.GameAgentFailureTracker.GameAgentContext;
import java.util.ArrayList;
import java.util.List;

public final class GameAgentFailureTrackerTest {
    public static void main(String[] args) throws Exception {
        List<CapturedFailure> captured = new ArrayList<>();
        GameAgentFailureTracker tracker = new GameAgentFailureTracker(captured::add);
        GameAgentContext context = new GameAgentContext(
                "ugc-safety-agent", "classify-player-banner", "asset-42", "finals-live", "priority-review");

        boolean rethrown = false;
        try {
            tracker.track(context, () -> { throw new IllegalStateException("review required"); });
        } catch (IllegalStateException expected) {
            rethrown = true;
        }

        require(rethrown, "the agent loop must receive the original failure");
        require(captured.size() == 1, "one failed step must produce one capture");
        CapturedFailure event = captured.get(0);
        require(event.agent().equals("ugc-safety-agent"), "agent forms the first grouping key");
        require(event.step().equals("classify-player-banner"), "step forms the second grouping key");
        require(event.assetId().equals("asset-42") && event.liveEventId().equals("finals-live"),
                "triage context must retain the asset and live event");
        System.out.println("PASS: failed moderation step was captured once and rethrown");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
