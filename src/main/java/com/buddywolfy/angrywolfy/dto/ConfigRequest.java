package com.buddywolfy.angrywolfy.dto;

import com.buddywolfy.angrywolfy.enums.ConfigType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record ConfigRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull ConfigType type,
        @NotBlank @Size(max = 500) String baseUrl,
        Map<String, String> headers
) {
}
