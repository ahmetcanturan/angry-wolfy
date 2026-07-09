package com.buddywolfy.angrywolfy.entity;

import com.buddywolfy.angrywolfy.enums.HttpMethod;
import com.buddywolfy.angrywolfy.enums.TargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Entity
@Table(name = "targets", indexes = @Index(name = "idx_targets_project_id", columnList = "project_id"))
public class Target {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String name;

    @Size(max = 2000)
    @Column(length = 2000)
    private String description;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @NotBlank
    @Size(max = 500)
    @Column(nullable = false, length = 500)
    private String path;

    /**
     * Optional per-target base URL that overrides the active environment's base
     * URL when set. Lets a single target point at a different domain (e.g. a
     * one-off host) without editing any {@link Config}. Blank/null means "use the
     * environment's base URL", the default behaviour. Headers still merge from
     * the config regardless.
     */
    @Size(max = 500)
    @Column(name = "base_url_override", length = 500)
    private String baseUrlOverride;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private HttpMethod method = HttpMethod.GET;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TargetType type = TargetType.REST;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "custom_headers", nullable = false)
    private Map<String, String> customHeaders = new LinkedHashMap<>();

    @Column(columnDefinition = "TEXT")
    private String body;

    @Size(max = 2000)
    @Column(length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Target() {
        // required by JPA
    }

    public Target(String name, String description, Project project, String path, HttpMethod method,
                   TargetType type, Map<String, String> customHeaders, String body, String notes) {
        this(name, description, project, path, null, method, type, customHeaders, body, notes);
    }

    public Target(String name, String description, Project project, String path, String baseUrlOverride,
                   HttpMethod method, TargetType type, Map<String, String> customHeaders, String body,
                   String notes) {
        this.name = name;
        this.description = description;
        this.project = project;
        this.path = path;
        this.baseUrlOverride = normalizeBaseUrl(baseUrlOverride);
        this.method = method != null ? method : HttpMethod.GET;
        this.type = type != null ? type : TargetType.REST;
        this.customHeaders = customHeaders != null ? new LinkedHashMap<>(customHeaders) : new LinkedHashMap<>();
        this.body = body;
        this.notes = notes;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
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

    public Project getProject() {
        return project;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getBaseUrlOverride() {
        return baseUrlOverride;
    }

    public void setBaseUrlOverride(String baseUrlOverride) {
        this.baseUrlOverride = normalizeBaseUrl(baseUrlOverride);
    }

    /** Trims and drops trailing slashes; blank becomes null so it reads as "unset". */
    static String normalizeBaseUrl(String url) {
        if (url == null) {
            return null;
        }
        String trimmed = url.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public void setMethod(HttpMethod method) {
        this.method = method != null ? method : HttpMethod.GET;
    }

    public TargetType getType() {
        return type;
    }

    public void setType(TargetType type) {
        this.type = type != null ? type : TargetType.REST;
    }

    public Map<String, String> getCustomHeaders() {
        return customHeaders;
    }

    public void setCustomHeaders(Map<String, String> customHeaders) {
        this.customHeaders = customHeaders != null ? new LinkedHashMap<>(customHeaders) : new LinkedHashMap<>();
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
