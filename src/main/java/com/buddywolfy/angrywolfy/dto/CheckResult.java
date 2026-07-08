package com.buddywolfy.angrywolfy.dto;

/**
 * The outcome of a single, real HTTP request sent to a target (the "Check"
 * action) — as opposed to an oha load test. Carries just enough to render the
 * response in the UI: the status line, timing, and the body plus a hint of how
 * to format it ({@code bodyKind}).
 */
public record CheckResult(
        int status,
        String reason,
        String method,
        String url,
        long durationMs,
        String contentType,
        long contentLength,
        /** "json" | "html" | "xml" | "text" — how the UI should render the body. */
        String bodyKind,
        boolean truncated,
        String body) {
}
