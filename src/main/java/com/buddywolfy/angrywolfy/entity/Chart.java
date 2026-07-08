package com.buddywolfy.angrywolfy.entity;

import com.buddywolfy.angrywolfy.dto.OhaResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A persisted load-test result, kept so its charts can be re-rendered later.
 *
 * <p>Only successful runs are stored. The full {@link OhaResult} lives in a
 * JSON column ({@code result_json}) — enough to regenerate every chart — while
 * a few headline metrics are flattened into their own columns so a run-history
 * list can be built without parsing JSON. The JSON column uses the same
 * {@link SqlTypes#JSON} mapping as {@code Config}, so it works on both SQLite
 * (stored as TEXT) and PostgreSQL (native JSON).
 *
 * <p>At most 50 rows are retained per target; older ones are trimmed on insert.
 */
@Entity
@Table(name = "charts", indexes = @Index(name = "idx_charts_target_id", columnList = "target_id"))
public class Chart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_id", nullable = false)
    private Target target;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "config_id", nullable = false)
    private Config config;

    // Flat headline metrics — queryable/sortable without touching the JSON.
    @Column(name = "total_requests", nullable = false)
    private long totalRequests;

    @Column(name = "requests_per_sec", nullable = false)
    private double requestsPerSec;

    @Column(name = "success_rate", nullable = false)
    private double successRate;

    @Column(name = "p95_ms", nullable = false)
    private double p95Ms;

    // Full result, used to re-render the charts on demand.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", nullable = false)
    private OhaResult result;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Chart() {
        // required by JPA
    }

    public Chart(Target target, Config config, OhaResult result) {
        this.target = target;
        this.config = config;
        this.result = result;
        this.totalRequests = totalRequestsOf(result);
        this.requestsPerSec = result.summary() != null ? result.summary().requestsPerSec() : 0;
        this.successRate = result.summary() != null ? result.summary().successRate() : 0;
        this.p95Ms = p95MsOf(result);
    }

    /** p95 in ms, or 0 when oha reported no percentile (a run with no successful responses). */
    private static double p95MsOf(OhaResult result) {
        OhaResult.Percentiles p = result.latencyPercentiles();
        if (p == null || p.p95() == null) {
            return 0;
        }
        return p.p95() * 1000;
    }

    private static long totalRequestsOf(OhaResult result) {
        if (result.statusCodeDistribution() == null) {
            return 0;
        }
        return result.statusCodeDistribution().values().stream().mapToLong(Integer::longValue).sum();
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Target getTarget() {
        return target;
    }

    public Config getConfig() {
        return config;
    }

    public long getTotalRequests() {
        return totalRequests;
    }

    public double getRequestsPerSec() {
        return requestsPerSec;
    }

    public double getSuccessRate() {
        return successRate;
    }

    public double getP95Ms() {
        return p95Ms;
    }

    public OhaResult getResult() {
        return result;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
