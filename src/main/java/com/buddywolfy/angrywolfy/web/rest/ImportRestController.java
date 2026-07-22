package com.buddywolfy.angrywolfy.web.rest;

import com.buddywolfy.angrywolfy.dto.ImportResult;
import com.buddywolfy.angrywolfy.service.CurlImportService;
import com.buddywolfy.angrywolfy.service.OpenApiImportService;
import com.buddywolfy.angrywolfy.service.PostmanImportService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Imports external API definitions into a project as targets. Structured so more
 * sources (HAR, …) can be added alongside Postman, OpenAPI, and cURL.
 */
@RestController
public class ImportRestController {

    private final PostmanImportService postmanImportService;
    private final OpenApiImportService openApiImportService;
    private final CurlImportService curlImportService;

    public ImportRestController(PostmanImportService postmanImportService,
                                OpenApiImportService openApiImportService,
                                CurlImportService curlImportService) {
        this.postmanImportService = postmanImportService;
        this.openApiImportService = openApiImportService;
        this.curlImportService = curlImportService;
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

    /**
     * {@code POST /api/projects/{projectId}/targets/import/curl} — body is one or
     * more curl commands as plain text. Each command becomes a target.
     */
    @PostMapping("/api/projects/{projectId}/targets/import/curl")
    public ImportResult importCurl(@PathVariable Long projectId, @RequestBody String commands) {
        return curlImportService.importCommands(projectId, commands);
    }
}
