package com.buddywolfy.angrywolfy.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TargetUrlSplitterTests {

    @Test
    void relativePathKeepsNoOverride() {
        var s = TargetUrlSplitter.split("/v1/payments", null);
        assertThat(s.baseUrl()).isNull();
        assertThat(s.path()).isEqualTo("/v1/payments");
    }

    @Test
    void absoluteUrlLiftsOriginIntoBase() {
        var s = TargetUrlSplitter.split("https://api.example.com/v1/payments", null);
        assertThat(s.baseUrl()).isEqualTo("https://api.example.com");
        assertThat(s.path()).isEqualTo("/v1/payments");
    }

    @Test
    void absoluteUrlKeepsPortAndQuery() {
        var s = TargetUrlSplitter.split("http://localhost:8080/search?q=1&x=2", null);
        assertThat(s.baseUrl()).isEqualTo("http://localhost:8080");
        assertThat(s.path()).isEqualTo("/search?q=1&x=2");
    }

    @Test
    void originOnlyUrlYieldsRootPath() {
        var s = TargetUrlSplitter.split("https://api.example.com", null);
        assertThat(s.baseUrl()).isEqualTo("https://api.example.com");
        assertThat(s.path()).isEqualTo("/");
    }

    @Test
    void queryImmediatelyAfterHostIsKept() {
        var s = TargetUrlSplitter.split("https://api.example.com?q=1", null);
        assertThat(s.baseUrl()).isEqualTo("https://api.example.com");
        assertThat(s.path()).isEqualTo("/?q=1");
    }

    @Test
    void postmanVariableBaseBecomesRelativePath() {
        var s = TargetUrlSplitter.split("{{baseUrl}}/v1/payments", null);
        assertThat(s.baseUrl()).isNull();
        assertThat(s.path()).isEqualTo("/v1/payments");
    }

    @Test
    void explicitOverrideWinsAndRawTreatedAsPath() {
        // When the caller already gives an override, the raw value is a path even
        // if it contains a domain — we don't second-guess an explicit choice.
        var s = TargetUrlSplitter.split("https://other.example.com/v1", "https://chosen.example.com");
        assertThat(s.baseUrl()).isEqualTo("https://chosen.example.com");
        assertThat(s.path()).isEqualTo("/v1");
    }

    @Test
    void relativePathGetsLeadingSlash() {
        var s = TargetUrlSplitter.split("v1/payments", null);
        assertThat(s.baseUrl()).isNull();
        assertThat(s.path()).isEqualTo("/v1/payments");
    }

    @Test
    void nullAndBlankBecomeRoot() {
        assertThat(TargetUrlSplitter.split(null, null).path()).isEqualTo("/");
        assertThat(TargetUrlSplitter.split("   ", null).path()).isEqualTo("/");
    }
}
