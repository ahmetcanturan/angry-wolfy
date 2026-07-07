package com.buddywolfy.angrywolfy.web.rest;

import com.buddywolfy.angrywolfy.dto.ConfigRequest;
import com.buddywolfy.angrywolfy.dto.ConfigResponse;
import com.buddywolfy.angrywolfy.service.ConfigService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
public class ConfigRestController {

    private final ConfigService configService;

    public ConfigRestController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/api/projects/{projectId}/configs")
    public List<ConfigResponse> getByProject(@PathVariable Long projectId) {
        return configService.getByProjectId(projectId).stream()
                .map(ConfigResponse::from)
                .toList();
    }

    @GetMapping("/api/configs/{id}")
    public ConfigResponse getById(@PathVariable Long id) {
        return ConfigResponse.from(configService.getById(id));
    }

    @PostMapping("/api/projects/{projectId}/configs")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ConfigResponse> create(@PathVariable Long projectId,
                                                  @Valid @RequestBody ConfigRequest request) {
        var created = configService.create(projectId, request.name(), request.type(),
                request.baseUrl(), request.headers());
        var body = ConfigResponse.from(created);
        return ResponseEntity.created(URI.create("/api/configs/" + created.getId())).body(body);
    }

    @PutMapping("/api/configs/{id}")
    public ConfigResponse update(@PathVariable Long id, @Valid @RequestBody ConfigRequest request) {
        var updated = configService.update(id, request.name(), request.type(),
                request.baseUrl(), request.headers());
        return ConfigResponse.from(updated);
    }

    @DeleteMapping("/api/configs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        configService.delete(id);
    }
}
