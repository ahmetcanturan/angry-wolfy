package com.buddywolfy.angrywolfy.dto;

import jakarta.validation.constraints.Positive;

/**
 * Client-supplied knobs for a single oha load-test run.
 *
 * <p>oha needs either a fixed number of requests ({@code -n}) or a duration
 * ({@code -z}). If {@link #durationSeconds} is set it takes precedence and
 * {@link #requests} is ignored; otherwise {@code requests} is used.
 *
 * @param requests        total number of requests to send ({@code -n}); defaults to 200
 * @param concurrency     number of concurrent connections ({@code -c}); defaults to 10
 * @param durationSeconds run for this many seconds instead of a fixed count ({@code -z})
 * @param queryPerSecond  rate limit in requests/sec ({@code -q}); unlimited when null
 * @param timeoutSeconds  per-request timeout in seconds ({@code -t}); defaults to oha's own
 */
public record OhaOptions(
        @Positive Integer requests,
        @Positive Integer concurrency,
        @Positive Integer durationSeconds,
        @Positive Double queryPerSecond,
        @Positive Integer timeoutSeconds) {

    private static final int DEFAULT_REQUESTS = 200;
    private static final int DEFAULT_CONCURRENCY = 10;

    /** Fills in defaults for any field the client left null. */
    public OhaOptions withDefaults() {
        return new OhaOptions(
                requests != null ? requests : DEFAULT_REQUESTS,
                concurrency != null ? concurrency : DEFAULT_CONCURRENCY,
                durationSeconds,
                queryPerSecond,
                timeoutSeconds);
    }

    /** True when the run is bounded by wall-clock time rather than a request count. */
    public boolean isDurationBased() {
        return durationSeconds != null;
    }
}
