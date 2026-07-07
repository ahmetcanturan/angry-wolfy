package com.buddywolfy.angrywolfy.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks in-flight oha processes by run id so a separate request can cancel
 * them. The run itself is still driven synchronously by {@link OhaRunner};
 * this registry only provides the side-channel needed to kill a live process
 * and record that the kill was a deliberate cancellation.
 */
@Component
public class OhaRunRegistry {

    private final Map<String, Process> live = new ConcurrentHashMap<>();
    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();

    /** Registers a running process under the given run id. */
    void register(String runId, Process process) {
        cancelled.remove(runId);
        live.put(runId, process);
    }

    /** Removes a run and clears its cancelled flag once it has fully finished. */
    void unregister(String runId) {
        live.remove(runId);
        cancelled.remove(runId);
    }

    /** True if this run was cancelled via {@link #cancel(String)}. */
    boolean wasCancelled(String runId) {
        return cancelled.contains(runId);
    }

    /**
     * Marks the run cancelled and force-kills its process, if still live.
     *
     * @return true if a live process was found and destroyed, false otherwise
     */
    public boolean cancel(String runId) {
        cancelled.add(runId);
        Process process = live.remove(runId);
        if (process == null) {
            return false;
        }
        process.destroyForcibly();
        return true;
    }
}
