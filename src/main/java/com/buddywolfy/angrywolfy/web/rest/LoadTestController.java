package com.buddywolfy.angrywolfy.web.rest;

import com.buddywolfy.angrywolfy.dto.CheckResult;
import com.buddywolfy.angrywolfy.dto.OhaOptions;
import com.buddywolfy.angrywolfy.dto.OhaResult;
import com.buddywolfy.angrywolfy.entity.Config;
import com.buddywolfy.angrywolfy.entity.Target;
import com.buddywolfy.angrywolfy.service.ChartService;
import com.buddywolfy.angrywolfy.service.ConfigService;
import com.buddywolfy.angrywolfy.service.OhaRunRegistry;
import com.buddywolfy.angrywolfy.service.OhaRunner;
import com.buddywolfy.angrywolfy.service.TargetCheckService;
import com.buddywolfy.angrywolfy.service.TargetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * Triggers oha load tests and allows cancelling an in-flight run.
 *
 * <p>The run itself is still synchronous — the POST blocks until oha finishes —
 * but each run is assigned an id (returned in the {@code X-Run-Id} header) and
 * registered with {@link OhaRunRegistry}. A concurrent
 * {@code DELETE /api/runs/{runId}} kills that process, causing the blocked POST
 * to return {@code 409 Conflict} (cancelled).
 */
@RestController
public class LoadTestController {

    /** Header carrying the run id so the client can cancel before the POST returns. */
    public static final String RUN_ID_HEADER = "X-Run-Id";

    private final TargetService targetService;
    private final ConfigService configService;
    private final OhaRunner ohaRunner;
    private final OhaRunRegistry runRegistry;
    private final ChartService chartService;
    private final TargetCheckService checkService;

    public LoadTestController(TargetService targetService,
                              ConfigService configService,
                              OhaRunner ohaRunner,
                              OhaRunRegistry runRegistry,
                              ChartService chartService,
                              TargetCheckService checkService) {
        this.targetService = targetService;
        this.configService = configService;
        this.ohaRunner = ohaRunner;
        this.runRegistry = runRegistry;
        this.chartService = chartService;
        this.checkService = checkService;
    }

    /**
     * {@code POST /api/targets/{targetId}/run?configId={id}[&runId={uuid}]}
     *
     * @param targetId the endpoint to load test
     * @param configId the environment (base URL + shared headers) to run against
     * @param runId    optional client-supplied run id so it can cancel before the
     *                 response arrives; a random one is generated if omitted
     * @param options  optional oha knobs; an empty/absent body uses defaults
     * @return oha's parsed JSON summary, with the run id echoed in {@code X-Run-Id}
     */
    @PostMapping("/api/targets/{targetId}/run")
    public ResponseEntity<OhaResult> run(@PathVariable Long targetId,
                                         @RequestParam Long configId,
                                         @RequestParam(required = false) String runId,
                                         @Valid @RequestBody(required = false) OhaOptions options) {
        Target target = targetService.getById(targetId);
        Config config = configService.getById(configId);
        requireSameProject(target, config);

        String id = (runId != null && !runId.isBlank()) ? runId : UUID.randomUUID().toString();
        OhaOptions effective = options != null ? options : new OhaOptions(null, null, null, null, null);
        // A cancelled/failed run throws before reaching here, so only successful
        // results are persisted (capped at 50 per target).
        OhaResult result = ohaRunner.run(id, target, config, effective);
        chartService.save(target, config, result);
        return ResponseEntity.ok().header(RUN_ID_HEADER, id).body(result);
    }

    /**
     * {@code POST /api/targets/{targetId}/check?configId={id}} — sends a single
     * real HTTP request to the target in the given environment and returns the
     * response (status, timing, and body) for display. No load test, no history.
     *
     * @param targetId the endpoint to hit once
     * @param configId the environment supplying the base URL and shared headers
     * @return the response summary and body
     */
    @PostMapping("/api/targets/{targetId}/check")
    public CheckResult check(@PathVariable Long targetId, @RequestParam Long configId) {
        Target target = targetService.getById(targetId);
        Config config = configService.getById(configId);
        requireSameProject(target, config);
        return checkService.check(target, config);
    }

    /**
     * {@code DELETE /api/runs/{runId}} — cancels an in-flight run by killing its
     * oha process. Returns 204 whether or not a live process was found, so the
     * client can fire-and-forget.
     */
    @DeleteMapping("/api/runs/{runId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable String runId) {
        runRegistry.cancel(runId);
    }

    /** A target may only be run against a config from its own project. */
    private void requireSameProject(Target target, Config config) {
        Long targetProjectId = target.getProject().getId();
        Long configProjectId = config.getProject().getId();
        if (!Objects.equals(targetProjectId, configProjectId)) {
            throw new IllegalArgumentException(
                    "Config " + config.getId() + " does not belong to the same project as target "
                            + target.getId());
        }
    }
}
