package com.buddywolfy.angrywolfy.dto;

import com.buddywolfy.angrywolfy.entity.Chart;

import java.time.Instant;

/**
 * Lightweight view of a stored run for the history list — flat headline metrics
 * only, without the full result JSON. Fetch {@code /api/charts/{id}} for the
 * complete {@link OhaResult} needed to re-render the charts.
 */
public record ChartSummary(
        Long id,
        Long targetId,
        Long configId,
        String configName,
        long totalRequests,
        double requestsPerSec,
        double successRate,
        double p95Ms,
        Instant createdAt
) {
    public static ChartSummary from(Chart chart) {
        // config is null for runs made with no environment.
        var config = chart.getConfig();
        return new ChartSummary(
                chart.getId(),
                chart.getTarget().getId(),
                config != null ? config.getId() : null,
                config != null ? config.getName() : "No environment",
                chart.getTotalRequests(),
                chart.getRequestsPerSec(),
                chart.getSuccessRate(),
                chart.getP95Ms(),
                chart.getCreatedAt()
        );
    }
}
