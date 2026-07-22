package com.buddywolfy.angrywolfy.service;

import com.buddywolfy.angrywolfy.dto.CheckResult;
import com.buddywolfy.angrywolfy.entity.Config;
import com.buddywolfy.angrywolfy.entity.Target;
import com.buddywolfy.angrywolfy.enums.TargetType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Sends a single, real HTTP request to a target evaluated in an environment and
 * returns the response for display — the "Check" action. This is intentionally
 * separate from the oha load test: one request, the full body, no aggregation.
 *
 * <p>The URL and merged headers are resolved exactly as they would be for a load
 * test, so a Check previews what a Run will actually hit.
 */
@Service
public class TargetCheckService {

    /** Response body is capped so a huge page can't be streamed back to the browser. */
    private static final int MAX_BODY_CHARS = 1_000_000;

    /** Headers the JDK client forbids callers from setting; we drop them silently. */
    private static final Set<String> RESTRICTED = Set.of(
            "connection", "content-length", "expect", "host", "upgrade");

    /** Methods that never carry a request body. */
    private static final Set<String> BODILESS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final OhaCommandBuilder urls;
    private final Duration timeout;

    public TargetCheckService(OhaCommandBuilder urls,
                              @Value("${angrywolfy.check.timeout-seconds:15}") long timeoutSeconds) {
        this.urls = urls;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    public CheckResult check(Target target, Config config) {
        if (target.getType() == TargetType.WEBSOCKET) {
            return checkWebSocket(target, config);
        }
        String url = urls.resolveUrl(config, target);
        String method = target.getMethod().name();

        HttpRequest request = buildRequest(target, config, url, method);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        long startNanos = System.nanoTime();
        HttpResponse<byte[]> response = send(client, request, method, url);
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;

        byte[] raw = response.body() != null ? response.body() : new byte[0];
        String full = new String(raw, StandardCharsets.UTF_8);
        boolean truncated = full.length() > MAX_BODY_CHARS;
        String body = truncated ? full.substring(0, MAX_BODY_CHARS) : full;

        String contentType = response.headers().firstValue("content-type").orElse("");
        String reason = HttpStatus.resolve(response.statusCode()) != null
                ? HttpStatus.resolve(response.statusCode()).getReasonPhrase() : "";

        return new CheckResult(
                response.statusCode(), reason, method, url, durationMs,
                contentType, raw.length, detectKind(contentType, body), truncated, body);
    }

    /**
     * The WebSocket flavour of a Check: open a real socket, send the target's
     * body (if any), and show whatever frames arrive within a short window —
     * enough to prove the endpoint speaks WS and see what it says first.
     */
    private CheckResult checkWebSocket(Target target, Config config) {
        String url = WsRunner.wsUri(urls.resolveUrl(config, target)).toString();
        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
        WebSocket.Builder builder = client.newWebSocketBuilder().connectTimeout(timeout);
        for (Map.Entry<String, String> h : urls.mergedHeaders(config, target).entrySet()) {
            String key = h.getKey() != null ? h.getKey().toLowerCase(Locale.ROOT) : "";
            if (key.isBlank() || RESTRICTED.contains(key) || key.startsWith("sec-websocket-")) {
                continue;
            }
            try {
                builder.header(h.getKey(), h.getValue() != null ? h.getValue() : "");
            } catch (IllegalArgumentException ignored) {
                // A header name the JDK refuses to set — skip it rather than fail the check.
            }
        }

        BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        long startNanos = System.nanoTime();
        WebSocket ws = openSocket(builder, url, frames);
        long handshakeMs = (System.nanoTime() - startNanos) / 1_000_000;

        try {
            if (target.getBody() != null && !target.getBody().isBlank()) {
                ws.sendText(target.getBody(), true).get(timeout.toSeconds(), TimeUnit.SECONDS);
            }
            String body = collectFrames(frames);
            boolean silent = body.isEmpty();
            if (silent) {
                body = "(connected — no message received within 2s)";
            }
            return new CheckResult(101, "Switching Protocols", "WS", url, handshakeMs,
                    "text/plain", body.length(), silent ? "text" : detectKind("", body), false, body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TargetCheckException("Check was interrupted", e);
        } catch (Exception e) {
            throw new TargetCheckException("WebSocket send failed: " + describeFailure(e), e);
        } finally {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "check done").get(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ws.abort();
            } catch (Exception e) {
                ws.abort();
            }
        }
    }

    private WebSocket openSocket(WebSocket.Builder builder, String url, BlockingQueue<String> frames) {
        WebSocket.Listener listener = new WebSocket.Listener() {
            private final StringBuilder buf = new StringBuilder();

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                buf.append(data);
                if (last) {
                    frames.offer(buf.toString());
                    buf.setLength(0);
                }
                webSocket.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                frames.offer("(binary frame, " + data.remaining() + " bytes)");
                webSocket.request(1);
                return null;
            }
        };
        try {
            return builder.buildAsync(URI.create(url), listener)
                    .get(timeout.plusSeconds(5).toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TargetCheckException("Check was interrupted", e);
        } catch (Exception e) {
            throw new TargetCheckException(
                    "Could not open a WebSocket to " + url + " — " + describeFailure(e)
                            + ". Check the base URL and that the server speaks WebSocket.", e);
        }
    }

    /** Drains up to five frames arriving within a ~2s window into one preview string. */
    private String collectFrames(BlockingQueue<String> frames) throws InterruptedException {
        StringBuilder out = new StringBuilder();
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        for (int i = 0; i < 5; i++) {
            long waitMs = (deadline - System.nanoTime()) / 1_000_000;
            if (waitMs <= 0) {
                break;
            }
            String frame = frames.poll(waitMs, TimeUnit.MILLISECONDS);
            if (frame == null) {
                break;
            }
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append(frame, 0, Math.min(frame.length(), MAX_BODY_CHARS - out.length()));
            if (out.length() >= MAX_BODY_CHARS) {
                break;
            }
        }
        return out.toString();
    }

    private HttpRequest buildRequest(Target target, Config config, String url, String method) {
        HttpRequest.Builder builder;
        try {
            builder = HttpRequest.newBuilder(URI.create(url)).timeout(timeout);
        } catch (IllegalArgumentException e) {
            throw new TargetCheckException("Invalid URL \"" + url + "\": " + e.getMessage(), e);
        }

        String body = target.getBody();
        boolean hasBody = body != null && !body.isBlank() && !BODILESS.contains(method);
        builder.method(method, hasBody
                ? HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)
                : HttpRequest.BodyPublishers.noBody());

        // Config headers first, then per-target overrides — same as a load test.
        for (Map.Entry<String, String> h : urls.mergedHeaders(config, target).entrySet()) {
            if (h.getKey() == null || h.getKey().isBlank()
                    || RESTRICTED.contains(h.getKey().toLowerCase(Locale.ROOT))) {
                continue;
            }
            try {
                builder.header(h.getKey(), h.getValue() != null ? h.getValue() : "");
            } catch (IllegalArgumentException ignored) {
                // A header name the JDK refuses to set — skip it rather than fail the check.
            }
        }
        return builder.build();
    }

    private HttpResponse<byte[]> send(HttpClient client, HttpRequest request, String method, String url) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (java.net.http.HttpTimeoutException e) {
            throw new TargetCheckException(
                    "Timed out after " + timeout.toSeconds() + "s waiting for " + method + " " + url);
        } catch (java.io.IOException e) {
            throw new TargetCheckException(
                    "Could not reach " + method + " " + url + " — " + describeFailure(e)
                            + ". Check the base URL and that the target service is running.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TargetCheckException("Check request was interrupted", e);
        }
    }

    /**
     * The JDK HTTP client reports connection failures as typed exceptions with
     * {@code null} messages, so map the exception <em>type</em> to friendly text.
     * Unknown-host and connection-refused both carry a {@link java.net.ConnectException},
     * so the more specific DNS case must be checked first.
     */
    private static String describeFailure(Throwable e) {
        boolean unresolved = false;
        boolean refused = false;
        String deepestMsg = null;
        String deepestType = e.getClass().getSimpleName();
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof java.nio.channels.UnresolvedAddressException
                    || c instanceof java.net.UnknownHostException) {
                unresolved = true;
            }
            if (c instanceof java.net.ConnectException
                    || c instanceof java.nio.channels.ClosedChannelException) {
                refused = true;
            }
            if (c.getMessage() != null && !c.getMessage().isBlank()) {
                deepestMsg = c.getMessage();
            }
            deepestType = c.getClass().getSimpleName();
            if (c.getCause() == c) {
                break;
            }
        }
        if (unresolved) {
            return "the host could not be resolved (DNS lookup failed)";
        }
        if (refused) {
            return "connection refused";
        }
        return deepestMsg != null ? deepestMsg : deepestType;
    }

    /** Prefer the declared Content-Type; fall back to sniffing the body's first non-space char. */
    private static String detectKind(String contentType, String body) {
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (ct.contains("json")) {
            return "json";
        }
        if (ct.contains("html")) {
            return "html";
        }
        if (ct.contains("xml")) {
            return "xml";
        }
        String t = body == null ? "" : body.stripLeading();
        if (t.startsWith("{") || t.startsWith("[")) {
            return "json";
        }
        if (t.startsWith("<")) {
            String lower = t.toLowerCase(Locale.ROOT);
            return (lower.startsWith("<!doctype html") || lower.contains("<html")) ? "html" : "xml";
        }
        return "text";
    }
}
