package com.buddywolfy.angrywolfy.dto;

import com.buddywolfy.angrywolfy.entity.Config;
import com.buddywolfy.angrywolfy.enums.ConfigType;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record ConfigResponse(
        Long id,
        Long projectId,
        String name,
        ConfigType type,
        String baseUrl,
        Map<String, String> headers,
        Instant createdAt,
        Instant updatedAt
) {
    public static ConfigResponse from(Config config) {
        return new ConfigResponse(
                config.getId(),
                config.getProject().getId(),
                config.getName(),
                config.getType(),
                config.getBaseUrl(),
                new LinkedHashMap<>(config.getHeaders()),
                config.getCreatedAt(),
                config.getUpdatedAt()
        );
    }
}
