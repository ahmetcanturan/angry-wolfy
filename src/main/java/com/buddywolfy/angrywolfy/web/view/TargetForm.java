package com.buddywolfy.angrywolfy.web.view;

import com.buddywolfy.angrywolfy.enums.HttpMethod;
import com.buddywolfy.angrywolfy.entity.Target;
import com.buddywolfy.angrywolfy.enums.TargetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TargetForm {

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 2000)
    private String description;

    @NotBlank
    @Size(max = 500)
    private String path;

    @NotNull
    private HttpMethod method = HttpMethod.GET;

    @NotNull
    private TargetType type = TargetType.REST;

    @Valid
    private List<HeaderEntryForm> headers = new ArrayList<>();

    private String body;

    @Size(max = 2000)
    private String notes;

    public TargetForm() {
    }

    public TargetForm(String name, String description, String path, HttpMethod method, TargetType type,
                       List<HeaderEntryForm> headers, String body, String notes) {
        this.name = name;
        this.description = description;
        this.path = path;
        this.method = method;
        this.type = type;
        this.headers = headers != null ? headers : new ArrayList<>();
        this.body = body;
        this.notes = notes;
    }

    public static TargetForm fromEntity(Target target) {
        return new TargetForm(
                target.getName(),
                target.getDescription(),
                target.getPath(),
                target.getMethod(),
                target.getType(),
                toHeaderEntryForms(target.getCustomHeaders()),
                target.getBody(),
                target.getNotes());
    }

    private static List<HeaderEntryForm> toHeaderEntryForms(Map<String, String> headers) {
        List<HeaderEntryForm> result = new ArrayList<>();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                result.add(new HeaderEntryForm(entry.getKey(), entry.getValue()));
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public void setMethod(HttpMethod method) {
        this.method = method;
    }

    public TargetType getType() {
        return type;
    }

    public void setType(TargetType type) {
        this.type = type;
    }

    public List<HeaderEntryForm> getHeaders() {
        return headers;
    }

    public void setHeaders(List<HeaderEntryForm> headers) {
        this.headers = headers;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
