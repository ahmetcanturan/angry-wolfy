package com.buddywolfy.angrywolfy.repository;

import com.buddywolfy.angrywolfy.entity.Chart;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChartRepository extends JpaRepository<Chart, Long> {

    /** The stored runs for a target, newest first (at most 50 are ever retained). */
    List<Chart> findByTargetIdOrderByCreatedAtDesc(Long targetId);

    /**
     * Newest runs across every target, with target/project/config eagerly fetched
     * so the dashboard feed can render each row without a lazy-load per run. Pass a
     * {@code PageRequest.of(0, n)} to cap the count.
     */
    @Query("""
            select c from Chart c
              join fetch c.target t
              join fetch t.project
              join fetch c.config
            order by c.createdAt desc, c.id desc
            """)
    List<Chart> findRecentWithDetails(Pageable pageable);

    /** Total requests fired across all stored runs (0 when there are none). */
    @Query("select coalesce(sum(c.totalRequests), 0) from Chart c")
    long sumTotalRequests();

    /** Mean success rate (0–1) across all stored runs, or 0 when there are none. */
    @Query("select coalesce(avg(c.successRate), 0) from Chart c")
    double avgSuccessRate();

    /** Mean p95 latency in ms across all stored runs, or 0 when there are none. */
    @Query("select coalesce(avg(c.p95Ms), 0) from Chart c")
    double avgP95Ms();

    /**
     * Per-project run rollup: {@code [projectId, runCount, lastRunAt, bestRps]}.
     * Only covers projects that have at least one stored run — the service merges
     * this with the full project list so run-less projects still appear.
     */
    @Query("""
            select t.project.id, count(c), max(c.createdAt), max(c.requestsPerSec)
            from Chart c join c.target t
            group by t.project.id
            """)
    List<Object[]> aggregateByProject();

    /**
     * Trims a target's history to the 50 newest rows by deleting the rest.
     *
     * <p>Written as native SQL because {@code DELETE ... WHERE id NOT IN (SELECT ...
     * LIMIT 50)} is the portable form that behaves identically on SQLite and
     * PostgreSQL. The {@code created_at DESC, id DESC} ordering keeps the "which
     * 50 to keep" decision deterministic when two rows share a timestamp.
     */
    @Modifying
    @Query(value = """
            DELETE FROM charts
            WHERE target_id = :targetId
              AND id NOT IN (
                SELECT id FROM charts
                WHERE target_id = :targetId
                ORDER BY created_at DESC, id DESC
                LIMIT 50
              )
            """, nativeQuery = true)
    int trimToNewest50(@Param("targetId") Long targetId);
}
