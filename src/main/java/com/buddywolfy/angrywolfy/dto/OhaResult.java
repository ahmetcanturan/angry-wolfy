package com.buddywolfy.angrywolfy.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed view of the JSON emitted by {@code oha --no-tui --output-format json}.
 *
 * <p>Field names match oha's output exactly so Jackson can bind stdout directly,
 * and the same object serializes back to the client unchanged. Unknown/rarely
 * used sections are ignored rather than mapped, so an oha upgrade that adds
 * fields won't break deserialization.
 *
 * <p>{@code responseTimeHistogram} and {@code firstBytePercentiles} are the
 * richest signals oha emits — the latency *shape* and the tail — so they are
 * mapped here to drive the dashboard charts. Histograms preserve oha's bucket
 * order via {@link LinkedHashMap}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OhaResult(
        Summary summary,
        Percentiles latencyPercentiles,
        LinkedHashMap<String, Integer> responseTimeHistogram,
        Percentiles firstBytePercentiles,
        Map<String, Integer> statusCodeDistribution,
        Map<String, Integer> errorDistribution) {

    /**
     * Aggregate throughput and timing for the whole run (seconds unless noted).
     *
     * <p>The latency-derived fields ({@code slowest}, {@code fastest},
     * {@code average}, {@code sizePerRequest}) are boxed because oha emits them as
     * {@code null} when a run produced no successful HTTP responses — e.g. every
     * request hit a connection error against an unreachable/wrong base URL. A
     * primitive would make Jackson reject the whole payload instead of letting the
     * run surface its error distribution.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(
            double successRate,
            double total,
            Double slowest,
            Double fastest,
            Double average,
            double requestsPerSec,
            long totalData,
            Long sizePerRequest,
            double sizePerSec) {
    }

    /**
     * Latency percentiles in seconds; keys are p10..p99.99 in oha's JSON.
     *
     * <p>Boxed for the same reason as {@link Summary}: with zero successful
     * responses there are no samples, so oha reports every percentile as
     * {@code null}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Percentiles(
            @com.fasterxml.jackson.annotation.JsonProperty("p10") Double p10,
            @com.fasterxml.jackson.annotation.JsonProperty("p25") Double p25,
            @com.fasterxml.jackson.annotation.JsonProperty("p50") Double p50,
            @com.fasterxml.jackson.annotation.JsonProperty("p75") Double p75,
            @com.fasterxml.jackson.annotation.JsonProperty("p90") Double p90,
            @com.fasterxml.jackson.annotation.JsonProperty("p95") Double p95,
            @com.fasterxml.jackson.annotation.JsonProperty("p99") Double p99,
            @com.fasterxml.jackson.annotation.JsonProperty("p99.9") Double p999,
            @com.fasterxml.jackson.annotation.JsonProperty("p99.99") Double p9999) {
    }
}
