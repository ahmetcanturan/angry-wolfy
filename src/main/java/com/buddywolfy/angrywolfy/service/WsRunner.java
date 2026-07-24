package com.buddywolfy.angrywolfy.service;

import com.buddywolfy.angrywolfy.dto.OhaOptions;
import com.buddywolfy.angrywolfy.dto.OhaResult;
import com.buddywolfy.angrywolfy.entity.Config;
import com.buddywolfy.angrywolfy.entity.Target;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * Native WebSocket load generator for {@code WEBSOCKET} targets — the oha
 * equivalent for sockets, built on the JDK's async WebSocket client with one
 * virtual thread per connection. Results are normalized into the same
 * {@link OhaResult} shape oha emits, so charts, history, and exports work
 * unchanged.
 *
 * <p>Three modes, chosen per run via {@link OhaOptions#wsMode()}:
 * <ul>
 *   <li>{@code echo} (default) — every connection sends the target's body and
 *       waits for the next frame back; latency is the message round-trip. Meant
 *       for echo/RPC-style servers; a push-only server will read as timeouts.</li>
 *   <li>{@code send} — fire-and-forget; latency is time-to-flush a message.</li>
 *   <li>{@code handshake} — a connection storm; latency is the WS upgrade time,
 *       "requests" is the number of connections opened, concurrency caps how
 *       many connect at once.</li>
 * </ul>
 *
 * <p>Run-shape knobs map naturally: concurrency = open connections, requests =
 * total messages (or connections in handshake mode), duration = pump until the
 * clock runs out, rate limit = messages/sec across the whole pool.
 */
@Service
public class WsRunner {

    private static final Logger log = LoggerFactory.getLogger(WsRunner.class);

    /** Headers the JDK client owns during the WS upgrade; sending them is an error. */
    private static final Set<String> RESTRICTED = Set.of(
            "connection", "upgrade", "host", "content-length", "expect");

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_MESSAGE_TIMEOUT = Duration.ofSeconds(10);

    /**
     * Ceiling on simultaneously-open sockets, whatever concurrency the client
     * asks for. Message modes open one real socket per unit of concurrency, so
     * an accidental 100000 would exhaust file descriptors and take the JVM down;
     * clamp it to something a dev box can actually hold.
     */
    private static final int MAX_CONNECTIONS = 5_000;

    private final OhaCommandBuilder urls;
    private final OhaRunRegistry registry;
    private final Duration maxRunDuration;

    public WsRunner(OhaCommandBuilder urls,
                    OhaRunRegistry registry,
                    @Value("${angrywolfy.ws.max-run-seconds:120}") long maxRunSeconds) {
        this.urls = urls;
        this.registry = registry;
        this.maxRunDuration = Duration.ofSeconds(maxRunSeconds);
    }

    /**
     * Executes a WebSocket load test and returns the oha-shaped summary. The run
     * is registered under {@code runId} so it can be cancelled mid-flight.
     *
     * @throws OhaCancelledException if the run was cancelled via the registry
     * @throws OhaExecutionException if no operation succeeded or the run was interrupted
     */
    public OhaResult run(String runId, Target target, Config config, OhaOptions options) {
        OhaOptions opts = options.withDefaults();
        String mode = opts.wsMode() != null ? opts.wsMode() : "echo";
        URI uri = wsUri(urls.resolveUrl(config, target));
        Map<String, String> headers = safeHeaders(urls.mergedHeaders(config, target));
        String payload = (target.getBody() != null && !target.getBody().isBlank())
                ? target.getBody() : "ping";
        Duration messageTimeout = opts.timeoutSeconds() != null
                ? Duration.ofSeconds(opts.timeoutSeconds()) : DEFAULT_MESSAGE_TIMEOUT;
        log.info("Running WS {} [{}]: {} (c={}, {})", mode, runId, uri, opts.concurrency(),
                opts.isDurationBased() ? opts.durationSeconds() + "s" : opts.requests() + " ops");

        RunState state = new RunState();
        registry.register(runId, state::kill);
        long startNanos = System.nanoTime();
        long deadlineNanos = startNanos + (opts.isDurationBased()
                ? Duration.ofSeconds(Math.min(opts.durationSeconds(), maxRunDuration.toSeconds())).toNanos()
                : maxRunDuration.toNanos());
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
            if (mode.equals("handshake")) {
                runHandshakes(client, uri, headers, opts, state, deadlineNanos);
            } else {
                runMessages(client, uri, headers, payload, mode.equals("echo"),
                        messageTimeout, opts, state, deadlineNanos);
            }
            if (registry.wasCancelled(runId)) {
                throw new OhaCancelledException("Run " + runId + " was cancelled");
            }
            double wallSeconds = (System.nanoTime() - startNanos) / 1e9;
            return buildResult(state, wallSeconds, uri);
        } finally {
            registry.unregister(runId);
        }
    }

    // ---- message modes (echo / send) --------------------------------------

    private void runMessages(HttpClient client, URI uri, Map<String, String> headers, String payload,
                             boolean echo, Duration messageTimeout, OhaOptions opts,
                             RunState state, long deadlineNanos) {
        int connections = clampConnections(opts.concurrency());
        // Global rate q msg/s spread across C connections = one message every C/q seconds each.
        long intervalNanos = opts.queryPerSecond() != null
                ? (long) (connections / opts.queryPerSecond() * 1e9) : 0;

        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        for (int c = 0; c < connections; c++) {
            // Count mode: split N messages across connections, remainder to the first few.
            int share = opts.isDurationBased() ? Integer.MAX_VALUE
                    : opts.requests() / connections + (c < opts.requests() % connections ? 1 : 0);
            if (share == 0) {
                continue;
            }
            pool.submit(() -> connectionLoop(client, uri, headers, payload, echo,
                    messageTimeout, share, intervalNanos, state, deadlineNanos));
        }
        awaitPool(pool, state, deadlineNanos, messageTimeout);
    }

    private void connectionLoop(HttpClient client, URI uri, Map<String, String> headers, String payload,
                                boolean echo, Duration messageTimeout, int share, long intervalNanos,
                                RunState state, long deadlineNanos) {
        FrameListener listener = new FrameListener(echo);
        WebSocket ws = connect(client, uri, headers, listener, state);
        if (ws == null) {
            return;
        }
        try {
            long nextSendNanos = System.nanoTime();
            for (int sent = 0; sent < share && !state.stopped.get()
                    && System.nanoTime() < deadlineNanos; sent++) {
                if (intervalNanos > 0 && !paceTo(nextSendNanos, state, deadlineNanos)) {
                    break;
                }
                nextSendNanos += intervalNanos;

                long t0 = System.nanoTime();
                try {
                    ws.sendText(payload, true)
                            .get(messageTimeout.toMillis(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    state.error("send failed: " + rootMessage(e));
                    return;
                }
                if (echo) {
                    awaitReply(listener, messageTimeout, t0, state);
                } else {
                    state.record((System.nanoTime() - t0) / 1e9, payload.length());
                }
            }
        } finally {
            state.sockets.remove(ws);
            closeQuietly(ws);
        }
    }

    /** Blocks until the next frame arrives and records RTT, or records why it didn't. */
    private void awaitReply(FrameListener listener, Duration timeout, long t0, RunState state) {
        Object frame;
        try {
            frame = listener.frames.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        switch (frame) {
            case null -> state.error("timeout: no reply within " + timeout.toSeconds() + "s");
            case FrameListener.Closed c -> state.error("connection closed (" + c.code() + ")");
            case FrameListener.Failed f -> state.error(f.message());
            case String s -> state.record((System.nanoTime() - t0) / 1e9, s.length());
            case FrameListener.Binary b -> state.record((System.nanoTime() - t0) / 1e9, b.bytes());
            default -> state.error("unexpected frame");
        }
    }

    // ---- handshake mode ----------------------------------------------------

    private void runHandshakes(HttpClient client, URI uri, Map<String, String> headers,
                               OhaOptions opts, RunState state, long deadlineNanos) {
        long intervalNanos = opts.queryPerSecond() != null
                ? (long) (1e9 / opts.queryPerSecond()) : 0;
        Semaphore inFlight = new Semaphore(clampConnections(opts.concurrency()));
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();

        long nextNanos = System.nanoTime();
        for (int i = 0; (opts.isDurationBased() || i < opts.requests())
                && !state.stopped.get() && System.nanoTime() < deadlineNanos; i++) {
            if (intervalNanos > 0 && !paceTo(nextNanos, state, deadlineNanos)) {
                break;
            }
            nextNanos += intervalNanos;
            try {
                inFlight.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            pool.submit(() -> {
                try {
                    long t0 = System.nanoTime();
                    WebSocket ws = connect(client, uri, headers, new FrameListener(false), state);
                    if (ws != null) {
                        state.record((System.nanoTime() - t0) / 1e9, 0);
                        state.sockets.remove(ws);
                        closeQuietly(ws);
                    }
                } finally {
                    inFlight.release();
                }
            });
        }
        awaitPool(pool, state, deadlineNanos, CONNECT_TIMEOUT);
    }

    // ---- connection plumbing ----------------------------------------------

    /** Opens one socket, recording a connect error and returning null on failure. */
    private WebSocket connect(HttpClient client, URI uri, Map<String, String> headers,
                              FrameListener listener, RunState state) {
        WebSocket.Builder builder = client.newWebSocketBuilder().connectTimeout(CONNECT_TIMEOUT);
        headers.forEach(builder::header);
        try {
            WebSocket ws = builder.buildAsync(uri, listener)
                    .get(CONNECT_TIMEOUT.plusSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
            state.sockets.add(ws);
            return ws;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            state.error("connect failed: " + rootMessage(e));
            return null;
        }
    }

    /** Sleeps until {@code atNanos}; false when the run stopped or timed out meanwhile. */
    private boolean paceTo(long atNanos, RunState state, long deadlineNanos) {
        while (System.nanoTime() < atNanos) {
            if (state.stopped.get() || System.nanoTime() >= deadlineNanos) {
                return false;
            }
            try {
                TimeUnit.NANOSECONDS.sleep(Math.min(atNanos - System.nanoTime(), 50_000_000L));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    /** Waits for all connection tasks; past the deadline the run is force-stopped. */
    private void awaitPool(ExecutorService pool, RunState state, long deadlineNanos, Duration grace) {
        pool.shutdown();
        try {
            long waitNanos = deadlineNanos - System.nanoTime() + grace.plusSeconds(5).toNanos();
            if (!pool.awaitTermination(Math.max(waitNanos, 0), TimeUnit.NANOSECONDS)) {
                state.kill();
                pool.awaitTermination(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            state.kill();
            Thread.currentThread().interrupt();
            throw new OhaExecutionException("WebSocket run was interrupted", e);
        }
    }

    private void closeQuietly(WebSocket ws) {
        try {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done")
                    .get(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ws.abort();
        } catch (Exception e) {
            ws.abort();
        }
    }

    // ---- result assembly ----------------------------------------------------

    private OhaResult buildResult(RunState state, double wallSeconds, URI uri) {
        long ok = state.ok.sum();
        long failed = state.errors.values().stream().mapToLong(LongAdder::sum).sum();
        if (ok == 0) {
            String topError = state.errors.entrySet().stream()
                    .max(Map.Entry.comparingByValue(
                            (a, b) -> Long.compare(a.sum(), b.sum())))
                    .map(Map.Entry::getKey)
                    .orElse("no operations ran");
            throw new OhaExecutionException(
                    "Could not complete any WebSocket operations against " + uri + " — all "
                            + Math.max(failed, 1) + " attempt(s) failed (" + topError
                            + "). Check the base URL and that the server speaks WebSocket.");
        }

        List<Double> lat = new ArrayList<>(state.latencies);
        lat.sort(null);
        double fastest = lat.getFirst();
        double slowest = lat.getLast();
        double average = lat.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        long attempts = ok + failed;
        long bytes = state.bytes.sum();

        OhaResult.Summary summary = new OhaResult.Summary(
                (double) ok / attempts, wallSeconds, slowest, fastest, average,
                attempts / wallSeconds, bytes, ok > 0 ? bytes / ok : null,
                bytes / wallSeconds);
        OhaResult.Percentiles percentiles = new OhaResult.Percentiles(
                pct(lat, 10), pct(lat, 25), pct(lat, 50), pct(lat, 75), pct(lat, 90),
                pct(lat, 95), pct(lat, 99), pct(lat, 99.9), pct(lat, 99.99));

        Map<String, Integer> statuses = new LinkedHashMap<>();
        statuses.put("ok", (int) ok);
        Map<String, Integer> errors = new LinkedHashMap<>();
        state.errors.forEach((k, v) -> errors.put(k, (int) v.sum()));

        return new OhaResult(summary, percentiles, histogram(lat, fastest, slowest),
                percentiles, statuses, errors);
    }

    private Double pct(List<Double> sorted, double p) {
        int idx = (int) Math.ceil(p / 100 * sorted.size()) - 1;
        return sorted.get(Math.clamp(idx, 0, sorted.size() - 1));
    }

    /** Eleven linear buckets keyed by their lower bound in seconds, oha-style. */
    private LinkedHashMap<String, Integer> histogram(List<Double> sorted, double fastest, double slowest) {
        int buckets = 11;
        double width = Math.max((slowest - fastest) / buckets, 1e-9);
        int[] counts = new int[buckets];
        for (double v : sorted) {
            counts[Math.min((int) ((v - fastest) / width), buckets - 1)]++;
        }
        LinkedHashMap<String, Integer> hist = new LinkedHashMap<>();
        for (int b = 0; b < buckets; b++) {
            hist.put(String.format(Locale.ROOT, "%.6f", fastest + b * width), counts[b]);
        }
        return hist;
    }

    // ---- helpers -------------------------------------------------------------

    /** Caps concurrency at {@link #MAX_CONNECTIONS}, noting when a request is clamped. */
    private int clampConnections(int requested) {
        if (requested > MAX_CONNECTIONS) {
            log.warn("Clamping WebSocket concurrency {} to {}", requested, MAX_CONNECTIONS);
            return MAX_CONNECTIONS;
        }
        return requested;
    }

    /** http(s) base URLs flip to ws(s); explicit ws(s) URLs pass through. */
    static URI wsUri(String url) {
        String ws = url.startsWith("https://") ? "wss://" + url.substring(8)
                : url.startsWith("http://") ? "ws://" + url.substring(7)
                : url;
        if (!ws.startsWith("ws://") && !ws.startsWith("wss://")) {
            throw new IllegalArgumentException(
                    "Can't derive a WebSocket URL from \"" + url + "\" — use an http(s) or ws(s) base URL.");
        }
        return URI.create(ws);
    }

    /** Drops headers the JDK WebSocket builder refuses to send. */
    private Map<String, String> safeHeaders(Map<String, String> merged) {
        Map<String, String> safe = new LinkedHashMap<>();
        merged.forEach((k, v) -> {
            String lower = k.toLowerCase(Locale.ROOT);
            if (!RESTRICTED.contains(lower) && !lower.startsWith("sec-websocket-")) {
                safe.put(k, v);
            }
        });
        return safe;
    }

    private static String rootMessage(Throwable t) {
        for (Throwable c = t; c != null && c.getCause() != c; c = c.getCause()) {
            if (c instanceof java.net.UnknownHostException
                    || c instanceof java.nio.channels.UnresolvedAddressException) {
                return "host not found";
            }
            if (c instanceof java.net.ConnectException
                    || c instanceof java.nio.channels.ClosedChannelException) {
                return "connection refused";
            }
            if (c instanceof java.net.http.HttpTimeoutException) {
                return "connect timed out";
            }
        }
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String msg = cause.getMessage();
        return msg == null || msg.isBlank() ? cause.getClass().getSimpleName() : msg;
    }

    // ---- run bookkeeping ------------------------------------------------------

    /** Shared counters plus the kill switch the cancel endpoint flips. */
    private static final class RunState {
        final AtomicBoolean stopped = new AtomicBoolean(false);
        final Set<WebSocket> sockets = ConcurrentHashMap.newKeySet();
        final Queue<Double> latencies = new ConcurrentLinkedQueue<>();
        final Map<String, LongAdder> errors = new ConcurrentHashMap<>();
        final LongAdder ok = new LongAdder();
        final LongAdder bytes = new LongAdder();

        void record(double latencySeconds, int frameBytes) {
            ok.increment();
            latencies.add(latencySeconds);
            bytes.add(frameBytes);
        }

        void error(String key) {
            errors.computeIfAbsent(key, k -> new LongAdder()).increment();
        }

        void kill() {
            stopped.set(true);
            sockets.forEach(WebSocket::abort);
        }
    }

    /**
     * Reassembles (possibly partial) frames into complete messages. In echo mode
     * they queue up for the sender to consume; otherwise they're dropped so an
     * unread queue can't grow without bound.
     */
    private static final class FrameListener implements WebSocket.Listener {

        record Closed(int code) {
        }

        record Failed(String message) {
        }

        record Binary(int bytes) {
        }

        final BlockingQueue<Object> frames = new LinkedBlockingQueue<>();
        private final boolean collect;
        private final StringBuilder textBuf = new StringBuilder();
        private int binaryBytes = 0;

        FrameListener(boolean collect) {
            this.collect = collect;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            textBuf.append(data);
            if (last) {
                offer(textBuf.toString());
                textBuf.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            binaryBytes += data.remaining();
            if (last) {
                offer(new Binary(binaryBytes));
                binaryBytes = 0;
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            frames.offer(new Closed(statusCode));
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            frames.offer(new Failed(rootMessage(error)));
        }

        private void offer(Object frame) {
            if (collect) {
                frames.offer(frame);
            }
        }
    }
}
