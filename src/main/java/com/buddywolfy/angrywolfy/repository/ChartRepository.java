package com.buddywolfy.angrywolfy.repository;

import com.buddywolfy.angrywolfy.entity.Chart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChartRepository extends JpaRepository<Chart, Long> {

    /** The stored runs for a target, newest first (at most 50 are ever retained). */
    List<Chart> findByTargetIdOrderByCreatedAtDesc(Long targetId);

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
