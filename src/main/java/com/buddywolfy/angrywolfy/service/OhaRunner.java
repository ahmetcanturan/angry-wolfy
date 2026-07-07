package com.buddywolfy.angrywolfy.service;

import com.buddywolfy.angrywolfy.dto.OhaOptions;
import com.buddywolfy.angrywolfy.dto.OhaResult;
import com.buddywolfy.angrywolfy.entity.Config;
import com.buddywolfy.angrywolfy.entity.Target;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Runs oha as an external process, blocking the calling thread until the load
 * test finishes, then parses its JSON output into an {@link OhaResult}.
 *
 * <p>Synchronous by design: the HTTP request that triggers a run waits for it.
 * A hard wall-clock cap ({@code angrywolfy.oha.max-run-seconds}) protects the
 * request thread from a run that never terminates.
 */
@Service
public class OhaRunner {

    private static final Logger log = LoggerFactory.getLogger(OhaRunner.class);

    private final OhaCommandBuilder commandBuilder;
    private final ObjectMapper objectMapper;
    private final OhaRunRegistry registry;
    private final Duration maxRunDuration;

    public OhaRunner(OhaCommandBuilder commandBuilder,
                     ObjectMapper objectMapper,
                     OhaRunRegistry registry,
                     @Value("${angrywolfy.oha.max-run-seconds:120}") long maxRunSeconds) {
        this.commandBuilder = commandBuilder;
        this.objectMapper = objectMapper;
        this.registry = registry;
        this.maxRunDuration = Duration.ofSeconds(maxRunSeconds);
    }

    /**
     * Executes a load test and returns the parsed summary. The process is
     * registered under {@code runId} so it can be cancelled mid-flight via
     * {@link OhaRunRegistry#cancel(String)}.
     *
     * @throws OhaCancelledException  if the run was cancelled via the registry
     * @throws OhaExecutionException  if oha cannot start, is interrupted, exceeds
     *                                the max run duration, exits non-zero, or emits
     *                                unparseable output
     */
    public OhaResult run(String runId, Target target, Config config, OhaOptions options) {
        List<String> command = commandBuilder.build(target, config, options.withDefaults());
        log.info("Running oha [{}]: {}", runId, String.join(" ", command));

        Process process = start(command);
        registry.register(runId, process);
        try {
            // Read stdout fully before waitFor so a large payload can't fill the
            // pipe buffer and deadlock the process.
            String stdout = drain(process.getInputStream());
            String stderr = drain(process.getErrorStream());

            awaitExit(process, command);
            int exit = process.exitValue();
            if (exit != 0) {
                throw new OhaExecutionException(
                        "oha exited with status " + exit + ": " + stderr.strip());
            }
            return parse(stdout);
        } catch (RuntimeException e) {
            // A cancel calls destroyForcibly(), which can surface as a killed exit
            // code OR as "Stream closed" while draining. Either way, if this run
            // was cancelled, report it as a cancellation rather than a failure.
            if (registry.wasCancelled(runId)) {
                throw new OhaCancelledException("Run " + runId + " was cancelled");
            }
            throw e;
        } finally {
            registry.unregister(runId);
        }
    }

    private Process start(List<String> command) {
        try {
            return new ProcessBuilder(command).start();
        } catch (IOException e) {
            throw new OhaExecutionException(
                    "Failed to start oha (is it installed and on PATH?): " + e.getMessage(), e);
        }
    }

    private void awaitExit(Process process, List<String> command) {
        try {
            if (!process.waitFor(maxRunDuration.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new OhaExecutionException(
                        "oha run exceeded " + maxRunDuration.toSeconds() + "s and was terminated");
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new OhaExecutionException("oha run was interrupted", e);
        }
    }

    private String drain(InputStream stream) {
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new OhaExecutionException("Failed to read oha output: " + e.getMessage(), e);
        }
    }

    private OhaResult parse(String json) {
        try {
            return objectMapper.readValue(json, OhaResult.class);
        } catch (RuntimeException e) {
            throw new OhaExecutionException("Failed to parse oha JSON output: " + e.getMessage(), e);
        }
    }
}
