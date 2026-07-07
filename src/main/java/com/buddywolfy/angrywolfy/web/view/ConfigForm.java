package com.buddywolfy.angrywolfy.web.view;

import com.buddywolfy.angrywolfy.entity.Config;
import com.buddywolfy.angrywolfy.enums.ConfigType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConfigForm {

    @NotBlank
    @Size(max = 255)
    private String name;

    @NotNull
    private ConfigType type = ConfigType.LOCAL;

    @NotBlank
    @Size(max = 500)
    private String baseUrl;

    @Valid
    private List<HeaderEntryForm> headers = new ArrayList<>();

    public ConfigForm() {
    }

    public ConfigForm(String name, ConfigType type, String baseUrl, List<HeaderEntryForm> headers) {
        this.name = name;
        this.type = type;
        this.baseUrl = baseUrl;
        this.headers = headers != null ? headers : new ArrayList<>();
    }

    public static ConfigForm fromEntity(Config config) {
        return new ConfigForm(config.getName(), config.getType(), config.getBaseUrl(),
                toHeaderEntryForms(config.getHeaders()));
    }

    private static List<HeaderEntryForm> toHeaderEntryForms(Map<String, String> headers) {
        List<HeaderEntryForm> result = new ArrayList<>();
        if (headers != null) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                result.add(new HeaderEntryForm(e.getKey(), e.getValue()));
            }
        }
        return result;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ConfigType getType() {
        return type;
    }

    public void setType(ConfigType type) {
        this.type = type;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public List<HeaderEntryForm> getHeaders() {
        return headers;
    }

    public void setHeaders(List<HeaderEntryForm> headers) {
        this.headers = headers;
    }
}
