package com.buddywolfy.angrywolfy.repository;

import com.buddywolfy.angrywolfy.entity.Config;
import com.buddywolfy.angrywolfy.enums.ConfigType;
import com.buddywolfy.angrywolfy.entity.Project;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ConfigRepositoryTests {

    @Autowired
    private ConfigRepository configRepository;

    @Autowired
    private ProjectRepository projectRepository;

    private Project persistedProject() {
        return projectRepository.save(new Project("Angrywolfy", "Test project", "/repos/angrywolfy"));
    }

    @Test
    void savesAndReadsBackAConfigWithJsonHeaders() {
        Project project = persistedProject();
        Map<String, String> headers = Map.of(
                "Authorization", "Bearer token123",
                "X-Api-Key", "abc");

        Config saved = configRepository.save(
                new Config(project, "Local dev", ConfigType.LOCAL, "http://localhost:8080", headers));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        Optional<Config> found = configRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Local dev");
        assertThat(found.get().getType()).isEqualTo(ConfigType.LOCAL);
        assertThat(found.get().getBaseUrl()).isEqualTo("http://localhost:8080");
        assertThat(found.get().getHeaders())
                .containsEntry("Authorization", "Bearer token123")
                .containsEntry("X-Api-Key", "abc");
    }

    @Test
    void defaultsTypeToLocalWhenNotSpecified() {
        Project project = persistedProject();

        Config saved = configRepository.save(
                new Config(project, "Default type", null, "http://localhost:8080", Map.of()));

        assertThat(saved.getType()).isEqualTo(ConfigType.LOCAL);
    }

    @Test
    void stripsTrailingSlashesFromBaseUrl() {
        Project project = persistedProject();

        Config saved = configRepository.save(
                new Config(project, "Trailing slash", ConfigType.STAGING, "https://staging.example.com///", Map.of()));

        assertThat(saved.getBaseUrl()).isEqualTo("https://staging.example.com");
    }

    @Test
    void findsConfigsByProjectId() {
        Project project = persistedProject();
        configRepository.save(new Config(project, "One", ConfigType.DEVELOPMENT, "https://dev.example.com", Map.of()));
        configRepository.save(new Config(project, "Two", ConfigType.PRODUCTION, "https://example.com", Map.of()));

        List<Config> found = configRepository.findByProjectId(project.getId());

        assertThat(found).hasSize(2);
    }

    @Test
    void rejectsBlankBaseUrl() {
        Project project = persistedProject();

        assertThatThrownBy(() -> configRepository.saveAndFlush(
                new Config(project, "Bad", ConfigType.LOCAL, "", Map.of())))
                .isInstanceOf(ConstraintViolationException.class);
    }
}
