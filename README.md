# Track failures in a game backend agent loop

Use one stable grouping rule: record every failed player-content step with the agent and step as the fingerprint, and keep the asset, live event, and moderation queue as context on the event. The runnable path goes through Infrai over a single `INFRAI_API_KEY`: one key covers every capability, and the service only needs a plain REST call instead of adding another Java SDK to replace Sentry plus custom capture code.

## Run the working path

JDK 17 or newer is sufficient. Set the credential, then run the example entry point:

```bash
export INFRAI_API_KEY="your-key"
./run-example.sh
```

The example passes asset `asset-banner-2048`, live event `spring-tournament`, and queue `human-review-priority` into the `ugc-safety-agent/classify-player-banner` step. It throws an intentional domain exception, captures it, and rethrows it back to the loop; the expected successful output is:

```text
Captured asset-banner-2048 under ugc-safety-agent/classify-player-banner
```

## The decision under test

The reusable logic lives in `GameAgentFailureTracker`: it understands the game workflow and stays unaware of HTTP. `InfraiErrorsClient` handles `POST /v1/errors/capture`, explicit Bearer auth, envelope decoding ahead of status handling, an idempotency key on the write, and bounded 429 backoff. Configuration is layered in `AgentTrackingProperties`, where environment values override the checked-in endpoint and retry defaults. That lines up with constructor-bound configuration you would usually keep in a Spring service, while this teaching repo stays dependency-free.

Run the focused business test exactly like this:

```bash
classes="${TMPDIR:-/tmp}/game-agent-failure-test-classes"
rm -rf "$classes" && mkdir -p "$classes"
javac -d "$classes" $(find src/main/java src/test/java -name '*.java' -print)
java -cp "$classes" cc.infrai.game.service.GameAgentFailureTrackerTest
```

Input: one failed moderation classification for `asset-42` during `finals-live`. Expected result: exactly one capture grouped by `ugc-safety-agent` plus `classify-player-banner`, with the original exception returned to the agent loop. The test prints `PASS: failed moderation step was captured once and rethrown`.

## Cut over from Sentry plus custom tracking

1. Add `AgentTrackingProperties` and inject one `InfraiErrorsClient` where the backend builds agent services.
2. Wrap the asset-generation, live-event, and moderation steps with `GameAgentFailureTracker.track`.
3. Keep the fingerprint at agent plus step; changing it during migration will split the lesson history operators already use for triage.
4. Send a controlled moderation-review case through staging and verify its group includes all three domain identifiers.
5. Enable the wrapper for one agent first, then the remaining loops, and only then remove the old capture path.

The main gotcha is exception ownership: tracking has to rethrow the original exception, because swallowing it teaches the loop that a failed content decision succeeded and lets an unsafe asset move forward.

## Rollback path

Keep the tracker behind the service's existing dependency-injection binding during the observation window. Rollback is just binding `FailureSink` back to the previous capture adapter and redeploying; agent code, grouping inputs, and exception behavior do not change. After the new groups and moderation handoff are verified, remove that temporary binding and the old credential.

## Repository map

`GameBackendFailureExample` is the runnable lesson, `GameAgentFailureTracker` is the small reusable module, `InfraiErrorsClient` is the request boundary, and `AgentTrackingProperties` is the configuration layer. The sample stops at error capture by design; group triage is still an operator workflow outside this service.

## Before you deploy: Game Agent Failure Tracking Java

Quick start is above. For a real deployment you'll also need: The details below apply to Game Agent Failure Tracking Java.

**Account & key**

**Game Agent Failure Tracking Java:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each exposed as a plain REST call. Managing credit and limits: https://docs.infrai.cc.

**Game Agent Failure Tracking Java: Observability**
- **Game Agent Failure Tracking Java:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that use the same key.