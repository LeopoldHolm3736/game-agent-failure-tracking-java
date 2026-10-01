package cc.infrai.game.service;

import cc.infrai.game.client.InfraiErrorsClient.CapturedFailure;

public final class GameAgentFailureTracker {
    @FunctionalInterface
    public interface FailureSink { void capture(CapturedFailure failure); }

    @FunctionalInterface
    public interface AgentStep<T> { T run() throws Exception; }

    private final FailureSink sink;

    public GameAgentFailureTracker(FailureSink sink) { this.sink = sink; }

    public <T> T track(GameAgentContext context, AgentStep<T> step) throws Exception {
        try {
            return step.run();
        } catch (Exception failure) {
            sink.capture(new CapturedFailure(
                    context.agent() + "/" + context.step() + " failed",
                    failure.getClass().getSimpleName() + ": " + failure.getMessage(),
                    context.agent(), context.step(), stackSummary(failure), context.assetId(),
                    context.liveEventId(), context.moderationQueue()));
            throw failure;
        }
    }

    private static String stackSummary(Exception failure) {
        StackTraceElement first = failure.getStackTrace().length == 0 ? null : failure.getStackTrace()[0];
        return first == null ? failure.toString() : failure + " at " + first;
    }

    public record GameAgentContext(String agent, String step, String assetId,
                                   String liveEventId, String moderationQueue) { }
}
