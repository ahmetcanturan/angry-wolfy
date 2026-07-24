package com.buddywolfy.angrywolfy.service;

import com.buddywolfy.angrywolfy.entity.Config;
import com.buddywolfy.angrywolfy.enums.ConfigType;
import com.buddywolfy.angrywolfy.entity.Project;
import com.buddywolfy.angrywolfy.repository.ChartRepository;
import com.buddywolfy.angrywolfy.repository.ConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigServiceTests {

    @Mock
    private ConfigRepository configRepository;

    @Mock
    private ProjectService projectService;

    @Mock
    private ChartRepository chartRepository;

    @InjectMocks
    private ConfigService configService;

    private Project project() {
        return new Project("Angrywolfy", "desc", "/repo");
    }

    @Test
    void createLooksUpProjectAndDelegatesToRepository() {
        Project project = project();
        when(projectService.getById(1L)).thenReturn(project);
        when(configRepository.save(ArgumentMatchers.any(Config.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Config result = configService.create(1L, "Local", ConfigType.LOCAL,
                "http://localhost:8080/", Map.of("Authorization", "Bearer x"));

        assertThat(result.getName()).isEqualTo("Local");
        assertThat(result.getBaseUrl()).isEqualTo("http://localhost:8080");
        assertThat(result.getHeaders()).containsEntry("Authorization", "Bearer x");
        verify(configRepository).save(ArgumentMatchers.any(Config.class));
    }

    @Test
    void getByIdReturnsConfigWhenFound() {
        Config existing = new Config(project(), "Local", ConfigType.LOCAL, "http://localhost", Map.of());
        when(configRepository.findById(1L)).thenReturn(Optional.of(existing));

        Config result = configService.getById(1L);

        assertThat(result).isSameAs(existing);
    }

    @Test
    void getByIdThrowsWhenMissing() {
        when(configRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> configService.getById(99L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getByProjectIdDelegatesToRepository() {
        List<Config> configs = List.of(new Config(project(), "Local", ConfigType.LOCAL, "http://localhost", Map.of()));
        when(configRepository.findByProjectId(1L)).thenReturn(configs);

        assertThat(configService.getByProjectId(1L)).isEqualTo(configs);
    }

    @Test
    void updateMutatesTheManagedEntityWithoutCallingSave() {
        Config existing = new Config(project(), "Old", ConfigType.LOCAL, "http://old", Map.of());
        when(configRepository.findById(1L)).thenReturn(Optional.of(existing));

        Config result = configService.update(1L, "New", ConfigType.PRODUCTION,
                "https://example.com/", Map.of("X-Api-Key", "k"));

        assertThat(result.getName()).isEqualTo("New");
        assertThat(result.getType()).isEqualTo(ConfigType.PRODUCTION);
        assertThat(result.getBaseUrl()).isEqualTo("https://example.com");
        assertThat(result.getHeaders()).containsEntry("X-Api-Key", "k");
        verify(configRepository, never()).save(ArgumentMatchers.any(Config.class));
    }

    @Test
    void updateThrowsWhenConfigMissing() {
        when(configRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> configService.update(99L, "n", ConfigType.LOCAL, "http://x", Map.of()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deleteDetachesStoredRunsThenDeletesConfig() {
        configService.delete(1L);

        verify(chartRepository).detachFromConfig(1L);
        verify(configRepository).deleteById(1L);
    }
}
