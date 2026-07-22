package com.buddywolfy.angrywolfy.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks in-flight load-test runs by run id so a separate request can cancel
 * them. Runs are still driven synchronously by their runner ({@link OhaRunner},
 * {@link WsRunner}); this registry only provides the side-channel needed to
 * stop a live run and record that the stop was a deliberate cancellation.
 * Runners register whatever "kill" means for them — destroying the oha
 * process, aborting open sockets.
 */
@Component
public class OhaRunRegistry {

    private final Map<String, Runnable> live = new ConcurrentHashMap<>();
    private final Set<String> cancelled = ConcurrentHashMap.newKeySet();

    /** Registers a live run and the action that kills it. */
    void register(String runId, Runnable killer) {
        cancelled.remove(runId);
        live.put(runId, killer);
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
     * Marks the run cancelled and kills it, if still live.
     *
     * @return true if a live run was found and killed, false otherwise
     */
    public boolean cancel(String runId) {
        cancelled.add(runId);
        Runnable killer = live.remove(runId);
        if (killer == null) {
            return false;
        }
        killer.run();
        return true;
    }
}
