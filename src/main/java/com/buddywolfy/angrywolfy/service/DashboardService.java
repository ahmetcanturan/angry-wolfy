package com.buddywolfy.angrywolfy.service;

import com.buddywolfy.angrywolfy.dto.DashboardData;
import com.buddywolfy.angrywolfy.entity.Project;
import com.buddywolfy.angrywolfy.repository.ChartRepository;
import com.buddywolfy.angrywolfy.repository.ConfigRepository;
import com.buddywolfy.angrywolfy.repository.ProjectRepository;
import com.buddywolfy.angrywolfy.repository.TargetRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles the Overview dashboard in a single read-only transaction. All the
 * lazy associations touched while mapping recent runs stay inside that
 * transaction, so the view never triggers a load.
 */
@Service
public class DashboardService {

    private static final int RECENT_RUN_LIMIT = 5;

    private final ProjectRepository projectRepository;
    private final TargetRepository targetRepository;
    private final ConfigRepository configRepository;
    private final ChartRepository chartRepository;
    private final OhaCommandBuilder urls;

    public DashboardService(ProjectRepository projectRepository, TargetRepository targetRepository,
                            ConfigRepository configRepository, ChartRepository chartRepository,
                            OhaCommandBuilder urls) {
        this.projectRepository = projectRepository;
        this.targetRepository = targetRepository;
        this.configRepository = configRepository;
        this.chartRepository = chartRepository;
        this.urls = urls;
    }

    @Transactional(readOnly = true)
    public DashboardData getDashboard() {
        List<DashboardData.RecentRun> recentRuns = chartRepository
                .findRecentWithDetails(PageRequest.of(0, RECENT_RUN_LIMIT))
                .stream()
                .map(c -> new DashboardData.RecentRun(
                        c.getId(),
                        c.getTarget().getProject().getId(),
                        c.getTarget().getProject().getName(),
                        c.getTarget().getId(),
                        c.getTarget().getName(),
                        c.getTarget().getMethod() != null ? c.getTarget().getMethod().name() : "GET",
                        urls.resolveUrl(c.getConfig(), c.getTarget()),
                        c.getConfig().getName(),
                        c.getTotalRequests(),
                        c.getRequestsPerSec(),
                        c.getSuccessRate(),
                        c.getP95Ms(),
                        c.getCreatedAt()))
                .toList();

        // The 5 newest projects, each joined against its run rollup (zeroes when
        // it has none yet).
        Map<Long, Object[]> byProject = new HashMap<>();
        for (Object[] row : chartRepository.aggregateByProject()) {
            byProject.put((Long) row[0], row);
        }
        List<DashboardData.ProjectRollup> projects = projectRepository.findTop5ByOrderByCreatedAtDesc().stream()
                .map(p -> toRollup(p, byProject.get(p.getId())))
                .toList();

        return new DashboardData(
                projectRepository.count(),
                targetRepository.count(),
                configRepository.count(),
                chartRepository.count(),
                chartRepository.sumTotalRequests(),
                chartRepository.avgSuccessRate(),
                chartRepository.avgP95Ms(),
                recentRuns,
                projects);
    }

    private static DashboardData.ProjectRollup toRollup(Project project, Object[] agg) {
        long runCount = agg != null ? ((Number) agg[1]).longValue() : 0;
        Instant lastRunAt = agg != null ? (Instant) agg[2] : null;
        double bestRps = agg != null && agg[3] != null ? ((Number) agg[3]).doubleValue() : 0;
        return new DashboardData.ProjectRollup(
                project.getId(), project.getName(), project.getCreatedAt(), runCount, lastRunAt, bestRps);
    }
}
