package com.buddywolfy.angrywolfy.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Client-supplied knobs for a single load-test run (oha for HTTP targets,
 * {@code WsRunner} for WebSocket targets).
 *
 * <p>The run needs either a fixed amount of work ({@code -n}) or a duration
 * ({@code -z}). If {@link #durationSeconds} is set it takes precedence and
 * {@link #requests} is ignored; otherwise {@code requests} is used.
 *
 * @param requests        total requests/messages to send ({@code -n}); defaults to 200
 * @param concurrency     concurrent connections ({@code -c}); defaults to 10
 * @param durationSeconds run for this many seconds instead of a fixed count ({@code -z})
 * @param queryPerSecond  rate limit in requests/sec ({@code -q}); unlimited when null
 * @param timeoutSeconds  per-request timeout in seconds ({@code -t}); defaults to oha's own
 * @param wsMode          WebSocket targets only: {@code echo} (send + await reply,
 *                        the default), {@code send} (fire-and-forget), or
 *                        {@code handshake} (connection storm). Ignored for HTTP.
 */
public record OhaOptions(
        @Positive Integer requests,
        @Positive Integer concurrency,
        @Positive Integer durationSeconds,
        @Positive Double queryPerSecond,
        @Positive Integer timeoutSeconds,
        @Pattern(regexp = "echo|send|handshake") String wsMode) {

    private static final int DEFAULT_REQUESTS = 200;
    private static final int DEFAULT_CONCURRENCY = 10;

    /** Fills in defaults for any field the client left null. */
    public OhaOptions withDefaults() {
        return new OhaOptions(
                requests != null ? requests : DEFAULT_REQUESTS,
                concurrency != null ? concurrency : DEFAULT_CONCURRENCY,
                durationSeconds,
                queryPerSecond,
                timeoutSeconds,
                wsMode);
    }

    /** True when the run is bounded by wall-clock time rather than a request count. */
    public boolean isDurationBased() {
        return durationSeconds != null;
    }
}
