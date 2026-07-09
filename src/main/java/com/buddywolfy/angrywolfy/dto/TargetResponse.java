package com.buddywolfy.angrywolfy.dto;

import com.buddywolfy.angrywolfy.entity.Target;
import com.buddywolfy.angrywolfy.enums.HttpMethod;
import com.buddywolfy.angrywolfy.enums.TargetType;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record TargetResponse(
        Long id,
        Long projectId,
        String name,
        String description,
        String path,
        String baseUrlOverride,
        HttpMethod method,
        TargetType type,
        Map<String, String> customHeaders,
        String body,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
    public static TargetResponse from(Target target) {
        return new TargetResponse(
                target.getId(),
                target.getProject().getId(),
                target.getName(),
                target.getDescription(),
                target.getPath(),
                target.getBaseUrlOverride(),
                target.getMethod(),
                target.getType(),
                new LinkedHashMap<>(target.getCustomHeaders()),
                target.getBody(),
                target.getNotes(),
                target.getCreatedAt(),
                target.getUpdatedAt()
        );
    }
}
