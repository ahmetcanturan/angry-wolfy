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

    /** Aggregate throughput and timing for the whole run (seconds unless noted). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(
            double successRate,
            double total,
            double slowest,
            double fastest,
            double average,
            double requestsPerSec,
            long totalData,
            long sizePerRequest,
            double sizePerSec) {
    }

    /** Latency percentiles in seconds; keys are p10..p99.99 in oha's JSON. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Percentiles(
            @com.fasterxml.jackson.annotation.JsonProperty("p10") double p10,
            @com.fasterxml.jackson.annotation.JsonProperty("p25") double p25,
            @com.fasterxml.jackson.annotation.JsonProperty("p50") double p50,
            @com.fasterxml.jackson.annotation.JsonProperty("p75") double p75,
            @com.fasterxml.jackson.annotation.JsonProperty("p90") double p90,
            @com.fasterxml.jackson.annotation.JsonProperty("p95") double p95,
            @com.fasterxml.jackson.annotation.JsonProperty("p99") double p99,
            @com.fasterxml.jackson.annotation.JsonProperty("p99.9") double p999,
            @com.fasterxml.jackson.annotation.JsonProperty("p99.99") double p9999) {
    }
}
