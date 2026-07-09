package com.buddywolfy.angrywolfy.web.rest;

import com.buddywolfy.angrywolfy.dto.TargetRequest;
import com.buddywolfy.angrywolfy.dto.TargetResponse;
import com.buddywolfy.angrywolfy.service.TargetService;
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
public class TargetRestController {

    private final TargetService targetService;

    public TargetRestController(TargetService targetService) {
        this.targetService = targetService;
    }

    @GetMapping("/api/projects/{projectId}/targets")
    public List<TargetResponse> getByProject(@PathVariable Long projectId) {
        return targetService.getByProjectId(projectId).stream()
                .map(TargetResponse::from)
                .toList();
    }

    @GetMapping("/api/targets/{id}")
    public TargetResponse getById(@PathVariable Long id) {
        return TargetResponse.from(targetService.getById(id));
    }

    @PostMapping("/api/projects/{projectId}/targets")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<TargetResponse> create(@PathVariable Long projectId,
                                                  @Valid @RequestBody TargetRequest request) {
        var created = targetService.create(request.name(), request.description(), projectId,
                request.path(), request.baseUrlOverride(), request.method(), request.type(),
                request.customHeaders(), request.body(), request.notes());
        var body = TargetResponse.from(created);
        return ResponseEntity.created(URI.create("/api/targets/" + created.getId())).body(body);
    }

    @PutMapping("/api/targets/{id}")
    public TargetResponse update(@PathVariable Long id, @Valid @RequestBody TargetRequest request) {
        var updated = targetService.update(id, request.name(), request.description(),
                request.path(), request.baseUrlOverride(), request.method(), request.type(),
                request.customHeaders(), request.body(), request.notes());
        return TargetResponse.from(updated);
    }

    @DeleteMapping("/api/targets/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        targetService.delete(id);
    }
}
