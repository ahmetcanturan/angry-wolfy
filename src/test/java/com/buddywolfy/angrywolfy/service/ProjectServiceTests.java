package com.buddywolfy.angrywolfy.service;

import com.buddywolfy.angrywolfy.entity.Project;
import com.buddywolfy.angrywolfy.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTests {

    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ProjectService projectService;

    @Test
    void createDelegatesToRepositoryAndReturnsSavedProject() {
        when(projectRepository.save(ArgumentMatchers.any(Project.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Project result = projectService.create("Angrywolfy", "desc", "/repo");

        assertThat(result.getName()).isEqualTo("Angrywolfy");
        assertThat(result.getDescription()).isEqualTo("desc");
        assertThat(result.getRepositoryPath()).isEqualTo("/repo");
        verify(projectRepository).save(ArgumentMatchers.any(Project.class));
    }

    @Test
    void getByIdReturnsProjectWhenFound() {
        Project existing = new Project("Angrywolfy", "desc", "/repo");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(existing));

        Project result = projectService.getById(1L);

        assertThat(result).isSameAs(existing);
    }

    @Test
    void getByIdThrowsWhenMissing() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getById(99L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getAllReturnsWhatRepositoryReturns() {
        List<Project> projects = List.of(
                new Project("A", "a", "/a"),
                new Project("B", "b", "/b"));
        when(projectRepository.findAll()).thenReturn(projects);

        assertThat(projectService.getAll()).hasSize(2).isEqualTo(projects);
    }

    @Test
    void updateMutatesTheManagedEntityWithoutCallingSave() {
        Project existing = new Project("Old name", "old desc", "/old");
        when(projectRepository.findById(1L)).thenReturn(Optional.of(existing));

        Project result = projectService.update(1L, "New name", "new desc", "/new");

        assertThat(result.getName()).isEqualTo("New name");
        assertThat(result.getDescription()).isEqualTo("new desc");
        assertThat(result.getRepositoryPath()).isEqualTo("/new");
        verify(projectRepository, never()).save(ArgumentMatchers.any(Project.class));
    }

    @Test
    void updateThrowsWhenProjectMissing() {
        when(projectRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.update(99L, "n", "d", "/r"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deleteDelegatesToRepository() {
        projectService.delete(1L);

        verify(projectRepository).deleteById(1L);
    }
}
