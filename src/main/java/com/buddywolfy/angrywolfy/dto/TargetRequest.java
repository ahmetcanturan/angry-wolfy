package com.buddywolfy.angrywolfy.dto;

import com.buddywolfy.angrywolfy.enums.HttpMethod;
import com.buddywolfy.angrywolfy.enums.TargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record TargetRequest(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 2000) String description,
        @NotBlank @Size(max = 500) String path,
        @Size(max = 500) String baseUrlOverride,
        @NotNull HttpMethod method,
        @NotNull TargetType type,
        Map<String, String> customHeaders,
        String body,
        @Size(max = 2000) String notes
) {
}
