package com.buddywolfy.angrywolfy.web.rest;

import com.buddywolfy.angrywolfy.dto.ImportResult;
import com.buddywolfy.angrywolfy.service.OpenApiImportService;
import com.buddywolfy.angrywolfy.service.PostmanImportService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Imports external API definitions into a project as targets. Structured so more
 * sources (cURL, HAR, …) can be added alongside Postman and OpenAPI.
 */
@RestController
public class ImportRestController {

    private final PostmanImportService postmanImportService;
    private final OpenApiImportService openApiImportService;

    public ImportRestController(PostmanImportService postmanImportService,
                                OpenApiImportService openApiImportService) {
        this.postmanImportService = postmanImportService;
        this.openApiImportService = openApiImportService;
    }

    /**
     * {@code POST /api/projects/{projectId}/targets/import/postman} — body is the
     * raw Postman Collection (v2.x) JSON. Each request becomes a target.
     */
    @PostMapping("/api/projects/{projectId}/targets/import/postman")
    public ImportResult importPostman(@PathVariable Long projectId, @RequestBody String collectionJson) {
        return postmanImportService.importCollection(projectId, collectionJson);
    }

    /**
     * {@code POST /api/projects/{projectId}/targets/import/openapi} — body is an
     * OpenAPI 3.x / Swagger 2.0 spec, JSON or YAML. Each operation becomes a target.
     */
    @PostMapping("/api/projects/{projectId}/targets/import/openapi")
    public ImportResult importOpenApi(@PathVariable Long projectId, @RequestBody String spec) {
        return openApiImportService.importSpec(projectId, spec);
    }
}
