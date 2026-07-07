package com.buddywolfy.angrywolfy.web.rest;

import com.buddywolfy.angrywolfy.dto.ChartSummary;
import com.buddywolfy.angrywolfy.dto.OhaResult;
import com.buddywolfy.angrywolfy.entity.Chart;
import com.buddywolfy.angrywolfy.service.ChartService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Reads stored run history. Successful runs are persisted automatically by
 * {@link LoadTestController} (at most 50 per target).
 */
@RestController
public class ChartRestController {

    private final ChartService chartService;

    public ChartRestController(ChartService chartService) {
        this.chartService = chartService;
    }

    /** {@code GET /api/targets/{targetId}/charts} — the target's run history, newest first. */
    @GetMapping("/api/targets/{targetId}/charts")
    public List<ChartSummary> listByTarget(@PathVariable Long targetId) {
        return chartService.getByTargetId(targetId).stream()
                .map(ChartSummary::from)
                .toList();
    }

    /**
     * {@code GET /api/charts/{id}} — the full stored result for one run, so the
     * client can re-render every chart from it.
     */
    @GetMapping("/api/charts/{id}")
    public OhaResult getResult(@PathVariable Long id) {
        Chart chart = chartService.getById(id);
        return chart.getResult();
    }
}
