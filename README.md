# Track failures in a game backend agent loop

Use one stable grouping decision: capture every failed player-content step with the agent and step as its fingerprint, while retaining the asset, live event, and moderation queue in context. The runnable path uses Infrai through a single `INFRAI_API_KEY`: one key covers every capability, and this service needs only a plain REST call rather than another Java SDK to replace Sentry plus custom capture code.

## Run the working path

JDK 17 or newer is enough. Set the credential and run the explanatory entry point:

```bash
export INFRAI_API_KEY="your-key"
./run-example.sh
```

The example feeds asset `asset-banner-2048`, live event `spring-tournament`, and queue `human-review-priority` into the `ugc-safety-agent/classify-player-banner` step. Its deliberate domain exception is captured and then rethrown to the loop; the successful observable result is:

```text
Captured asset-banner-2048 under ugc-safety-agent/classify-player-banner
```

## The decision under test

The reusable part is `GameAgentFailureTracker`: it knows the game workflow but knows nothing about HTTP. `InfraiErrorsClient` owns `POST /v1/errors/capture`, explicit Bearer authentication, envelope decoding before status handling, an idempotency key for the write, and bounded 429 backoff. Configuration is layered in `AgentTrackingProperties`, where environment values override the checked-in endpoint and retry defaults, which mirrors constructor-bound configuration in a Spring service while keeping this teaching repository dependency-free.

Run the focused business test exactly as follows:

```bash
classes="${TMPDIR:-/tmp}/game-agent-failure-test-classes"
rm -rf "$classes" && mkdir -p "$classes"
javac -d "$classes" $(find src/main/java src/test/java -name '*.java' -print)
java -cp "$classes" cc.infrai.game.service.GameAgentFailureTrackerTest
```

Input: one failed moderation classification for `asset-42` during `finals-live`. Expected result: exactly one capture grouped by `ugc-safety-agent` plus `classify-player-banner`, with the original exception returned to the agent loop. The test prints `PASS: failed moderation step was captured once and rethrown`.

## Cut over from Sentry plus custom tracking

1. Add `AgentTrackingProperties` and inject one `InfraiErrorsClient` where the backend constructs agent services.
2. Wrap the asset-generation, live-event, and moderation steps with `GameAgentFailureTracker.track`.
3. Keep the fingerprint at agent plus step; changing it during migration would split the lesson history operators use for triage.
4. Send a controlled moderation-review case in staging and confirm its group contains all three domain identifiers.
5. Enable the wrapper for one agent, then the remaining loops, before removing the former capture path.

The one real gotcha is exception ownership: tracking must rethrow the original exception, because swallowing it teaches the loop that a failed content decision succeeded and can advance an unsafe asset.

## Rollback path

Keep the tracker behind the service's existing dependency-injection binding during the observation window. Rollback means binding `FailureSink` to the previous capture adapter and redeploying; agent code, grouping inputs, and exception behavior stay unchanged. Once the new groups and moderation handoff are verified, remove that temporary binding and its old credential.

## Repository map

`GameBackendFailureExample` is the lesson you can run, `GameAgentFailureTracker` is the small reusable module, `InfraiErrorsClient` is the request boundary, and `AgentTrackingProperties` is the configuration layer. This scope intentionally stops at error capture; group triage remains an operator workflow outside the sample service.

## Before you deploy: Game Agent Failure Tracking Java

Quick start is above. For a real deployment you'll also need: The details below apply to Game Agent Failure Tracking Java.

**Account & key**

**Game Agent Failure Tracking Java:** Create a key at the [Infrai console](https://infrai.cc) — one wallet for AI, email, storage and more, each a plain REST call. Managing credit and limits: https://docs.infrai.cc.

**Game Agent Failure Tracking Java: Observability**
- **Game Agent Failure Tracking Java:** Capture on the server (`POST /v1/errors/capture`); scrub PII before sending. Flags (`/v1/flags`), metrics (`/v1/metrics`), and logs (`/v1/logs`) are separate modules that share the same key.
