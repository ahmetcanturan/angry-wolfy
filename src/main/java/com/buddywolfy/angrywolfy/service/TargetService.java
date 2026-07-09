package com.buddywolfy.angrywolfy.service;

import com.buddywolfy.angrywolfy.enums.HttpMethod;
import com.buddywolfy.angrywolfy.entity.Target;
import com.buddywolfy.angrywolfy.enums.TargetType;
import com.buddywolfy.angrywolfy.repository.TargetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class TargetService {

    private final TargetRepository targetRepository;
    private final ProjectService projectService;

    public TargetService(TargetRepository targetRepository, ProjectService projectService) {
        this.targetRepository = targetRepository;
        this.projectService = projectService;
    }

    @Transactional
    public Target create(String name, String description, Long projectId, String path, HttpMethod method,
                          TargetType type, Map<String, String> customHeaders, String body, String notes) {
        return create(name, description, projectId, path, null, method, type, customHeaders, body, notes);
    }

    @Transactional
    public Target create(String name, String description, Long projectId, String path, String baseUrlOverride,
                          HttpMethod method, TargetType type, Map<String, String> customHeaders, String body,
                          String notes) {
        var project = projectService.getById(projectId);
        return targetRepository.save(new Target(name, description, project, path, baseUrlOverride, method,
                type, customHeaders, body, notes));
    }

    @Transactional(readOnly = true)
    public Target getById(Long id) {
        return targetRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Target not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Target> getAll() {
        return targetRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Target> getByProjectId(Long projectId) {
        return targetRepository.findByProjectId(projectId);
    }

    @Transactional
    public Target update(Long id, String name, String description, String path, HttpMethod method,
                          TargetType type, Map<String, String> customHeaders, String body, String notes) {
        return update(id, name, description, path, null, method, type, customHeaders, body, notes);
    }

    @Transactional
    public Target update(Long id, String name, String description, String path, String baseUrlOverride,
                          HttpMethod method, TargetType type, Map<String, String> customHeaders, String body,
                          String notes) {
        Target target = getById(id);
        target.setName(name);
        target.setDescription(description);
        target.setPath(path);
        target.setBaseUrlOverride(baseUrlOverride);
        target.setMethod(method);
        target.setType(type);
        target.setCustomHeaders(customHeaders);
        target.setBody(body);
        target.setNotes(notes);
        return target;
    }

    @Transactional
    public void delete(Long id) {
        targetRepository.deleteById(id);
    }
}
