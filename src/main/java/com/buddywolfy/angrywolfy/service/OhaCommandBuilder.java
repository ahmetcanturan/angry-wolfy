package com.buddywolfy.angrywolfy.service;

import com.buddywolfy.angrywolfy.dto.OhaOptions;
import com.buddywolfy.angrywolfy.entity.Config;
import com.buddywolfy.angrywolfy.entity.Target;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the {@code oha} command line for a load test against a {@link Target}
 * evaluated in a given {@link Config} (environment).
 *
 * <p>Arguments are returned as a list and handed to {@link ProcessBuilder}
 * verbatim — they are never concatenated into a shell string, so URLs, headers,
 * and bodies cannot break out into shell interpretation.
 */
@Component
public class OhaCommandBuilder {

    /** Path to the oha binary; override with {@code angrywolfy.oha.binary} if not on PATH. */
    private final String binary;

    public OhaCommandBuilder(@Value("${angrywolfy.oha.binary:oha}") String binary) {
        this.binary = binary;
    }

    /**
     * @param target  endpoint to hit (path, method, body, per-target headers)
     * @param config  environment supplying the base URL and shared headers
     * @param options run knobs; callers should pass {@link OhaOptions#withDefaults()}
     * @return the full argv, starting with the oha binary
     */
    public List<String> build(Target target, Config config, OhaOptions options) {
        List<String> args = new ArrayList<>();
        args.add(binary);

        // Always machine-readable, never the interactive TUI.
        args.add("--no-tui");
        args.add("--output-format");
        args.add("json");

        // Load shape: duration wins over a fixed request count when both are set.
        if (options.isDurationBased()) {
            args.add("-z");
            args.add(options.durationSeconds() + "s");
        } else {
            args.add("-n");
            args.add(String.valueOf(options.requests()));
        }
        args.add("-c");
        args.add(String.valueOf(options.concurrency()));

        if (options.queryPerSecond() != null) {
            args.add("-q");
            args.add(String.valueOf(options.queryPerSecond()));
        }
        if (options.timeoutSeconds() != null) {
            args.add("-t");
            args.add(options.timeoutSeconds() + "s");
        }

        // HTTP method.
        args.add("-m");
        args.add(target.getMethod().name());

        // Merged headers: config-level first, then per-target overrides win.
        for (Map.Entry<String, String> header : mergedHeaders(config, target).entrySet()) {
            args.add("-H");
            args.add(header.getKey() + ": " + header.getValue());
        }

        // Request body, if any.
        if (target.getBody() != null && !target.getBody().isBlank()) {
            args.add("-d");
            args.add(target.getBody());
        }

        // The URL must be the last positional argument.
        args.add(resolveUrl(config, target));
        return args;
    }

    /**
     * Joins a base URL and the target path into a single absolute URL. The
     * target's own {@code baseUrlOverride} wins when set, letting one target
     * point at a different domain than the environment; otherwise the config's
     * base URL is used. Both are already trailing-slash-normalized.
     *
     * <p>{@code config} may be null — a target that carries its own absolute URL
     * (an override, e.g. an imported WebSocket target) can run with no
     * environment at all. A target that has neither an override nor a config to
     * borrow a base URL from can't be resolved and is rejected with a clear message.
     */
    String resolveUrl(Config config, Target target) {
        String override = target.getBaseUrlOverride();
        String base = (override != null && !override.isBlank()) ? override
                : (config != null ? config.getBaseUrl() : null);
        if (base == null || base.isBlank()) {
            throw new IllegalArgumentException(
                    "Target \"" + target.getName() + "\" has no base URL — pick an environment "
                            + "or set a base URL on the target.");
        }
        String path = target.getPath();
        if (path == null || path.isBlank()) {
            return base;
        }
        return path.startsWith("/") ? base + path : base + "/" + path;
    }

    /** Config headers as the baseline, overridden by any same-named target headers. */
    Map<String, String> mergedHeaders(Config config, Target target) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (config != null) {
            merged.putAll(config.getHeaders());
        }
        merged.putAll(target.getCustomHeaders());
        return merged;
    }
}
