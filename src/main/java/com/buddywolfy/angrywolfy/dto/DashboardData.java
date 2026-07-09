package com.buddywolfy.angrywolfy.dto;

import java.time.Instant;
import java.util.List;

/**
 * Everything the Overview dashboard renders, assembled in one read so the view
 * is a straight bind with no logic. Counts and all-time aggregates sit at the
 * top; {@code recentRuns} feeds the activity list and {@code projects} the
 * per-project rollup.
 */
public record DashboardData(
        long projectCount,
        long targetCount,
        long configCount,
        long runCount,
        long totalRequests,
        double avgSuccessRate,
        double avgP95Ms,
        List<RecentRun> recentRuns,
        List<ProjectRollup> projects
) {

    /**
     * One row of the recent-activity feed, denormalised for display.
     * {@code chartId} is the stored run's id, so the newest one can be fetched
     * from {@code /api/charts/{id}} to render the featured charts.
     */
    public record RecentRun(
            Long chartId,
            Long projectId,
            String projectName,
            Long targetId,
            String targetName,
            String httpMethod,
            String endpoint,
            String configName,
            long totalRequests,
            double requestsPerSec,
            double successRate,
            double p95Ms,
            Instant createdAt
    ) {}

    /**
     * A project with its headline run stats; {@code lastRunAt} is null when it
     * has none. {@code createdAt} drives the "latest created" ordering.
     */
    public record ProjectRollup(
            Long id,
            String name,
            Instant createdAt,
            long runCount,
            Instant lastRunAt,
            double bestRps
    ) {}
}
