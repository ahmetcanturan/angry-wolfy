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
        return new ChartSummary(
                chart.getId(),
                chart.getTarget().getId(),
                chart.getConfig().getId(),
                chart.getConfig().getName(),
                chart.getTotalRequests(),
                chart.getRequestsPerSec(),
                chart.getSuccessRate(),
                chart.getP95Ms(),
                chart.getCreatedAt()
        );
    }
}
