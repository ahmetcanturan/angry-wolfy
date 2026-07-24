package com.buddywolfy.angrywolfy.service;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.buddywolfy.angrywolfy.entity.Config;
import com.buddywolfy.angrywolfy.enums.ConfigType;
import com.buddywolfy.angrywolfy.repository.ChartRepository;
import com.buddywolfy.angrywolfy.repository.ConfigRepository;

@Service
public class ConfigService {

    private final ConfigRepository configRepository;
    private final ProjectService projectService;
    private final ChartRepository chartRepository;

    public ConfigService(ConfigRepository configRepository, ProjectService projectService,
                         ChartRepository chartRepository) {
        this.configRepository = configRepository;
        this.projectService = projectService;
        this.chartRepository = chartRepository;
    }

    @Transactional
    public Config create(Long projectId, String name, ConfigType type, String baseUrl, Map<String, String> headers) {
        var project = projectService.getById(projectId);
        return configRepository.save(new Config(project, name, type, baseUrl, headers));
    }

    @Transactional(readOnly = true)
    public Config getById(Long id) {
        return configRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Config not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Config> getAll() {
        return configRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Config> getByProjectId(Long projectId) {
        return configRepository.findByProjectId(projectId);
    }

    @Transactional
    public Config update(Long id, String name, ConfigType type, String baseUrl, Map<String, String> headers) {
        Config config = getById(id);
        config.setName(name);
        config.setType(type);
        config.setBaseUrl(baseUrl);
        config.setHeaders(headers);
        return config;
    }

    @Transactional
    public void delete(Long id) {
        chartRepository.detachFromConfig(id);
        configRepository.deleteById(id);
    }
}
