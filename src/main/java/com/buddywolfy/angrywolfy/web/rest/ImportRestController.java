package com.buddywolfy.angrywolfy.web.rest;

import com.buddywolfy.angrywolfy.dto.ImportResult;
import com.buddywolfy.angrywolfy.service.PostmanImportService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Imports external API definitions into a project as targets. Structured so more
 * sources (OpenAPI, cURL, HAR, …) can be added alongside Postman.
 */
@RestController
public class ImportRestController {

    private final PostmanImportService postmanImportService;

    public ImportRestController(PostmanImportService postmanImportService) {
        this.postmanImportService = postmanImportService;
    }

    /**
     * {@code POST /api/projects/{projectId}/targets/import/postman} — body is the
     * raw Postman Collection (v2.x) JSON. Each request becomes a target.
     */
    @PostMapping("/api/projects/{projectId}/targets/import/postman")
    public ImportResult importPostman(@PathVariable Long projectId, @RequestBody String collectionJson) {
        return postmanImportService.importCollection(projectId, collectionJson);
    }
}
