package com.buddywolfy.angrywolfy.service;

import com.buddywolfy.angrywolfy.entity.HttpMethod;
import com.buddywolfy.angrywolfy.entity.Project;
import com.buddywolfy.angrywolfy.entity.Target;
import com.buddywolfy.angrywolfy.entity.TargetType;
import com.buddywolfy.angrywolfy.repository.TargetRepository;
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
class TargetServiceTests {

    @Mock
    private TargetRepository targetRepository;

    @Mock
    private ProjectService projectService;

    @InjectMocks
    private TargetService targetService;

    private Project project() {
        return new Project("Angrywolfy", "desc", "/repo");
    }

    @Test
    void createLooksUpProjectAndDelegatesToRepository() {
        Project project = project();
        when(projectService.getById(1L)).thenReturn(project);
        when(targetRepository.save(ArgumentMatchers.any(Target.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Target result = targetService.create("Get users", "desc", 1L, "/api/users", HttpMethod.GET,
                TargetType.REST, Map.of("Authorization", "Bearer x"), null, 25.0, "note");

        assertThat(result.getName()).isEqualTo("Get users");
        assertThat(result.getPath()).isEqualTo("/api/users");
        assertThat(result.getMethod()).isEqualTo(HttpMethod.GET);
        assertThat(result.getType()).isEqualTo(TargetType.REST);
        assertThat(result.getCustomHeaders()).containsEntry("Authorization", "Bearer x");
        assertThat(result.getRps()).isEqualTo(25.0);
        assertThat(result.getNotes()).isEqualTo("note");
        verify(targetRepository).save(ArgumentMatchers.any(Target.class));
    }

    @Test
    void getByIdReturnsTargetWhenFound() {
        Target existing = new Target("Get users", null, project(), "/api/users", HttpMethod.GET,
                TargetType.REST, Map.of(), null, null, null);
        when(targetRepository.findById(1L)).thenReturn(Optional.of(existing));

        Target result = targetService.getById(1L);

        assertThat(result).isSameAs(existing);
    }

    @Test
    void getByIdThrowsWhenMissing() {
        when(targetRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> targetService.getById(99L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getByProjectIdDelegatesToRepository() {
        List<Target> targets = List.of(new Target("One", null, project(), "/a", HttpMethod.GET,
                TargetType.REST, Map.of(), null, null, null));
        when(targetRepository.findByProjectId(1L)).thenReturn(targets);

        assertThat(targetService.getByProjectId(1L)).isEqualTo(targets);
    }

    @Test
    void updateMutatesTheManagedEntityWithoutCallingSave() {
        Target existing = new Target("Old", null, project(), "/old", HttpMethod.GET, TargetType.REST,
                Map.of(), null, null, null);
        when(targetRepository.findById(1L)).thenReturn(Optional.of(existing));

        Target result = targetService.update(1L, "New", "new desc", "/new", HttpMethod.POST,
                TargetType.GRAPHQL, Map.of("X-Api-Key", "k"), "{}", 30.0, "updated note");

        assertThat(result.getName()).isEqualTo("New");
        assertThat(result.getPath()).isEqualTo("/new");
        assertThat(result.getMethod()).isEqualTo(HttpMethod.POST);
        assertThat(result.getType()).isEqualTo(TargetType.GRAPHQL);
        assertThat(result.getCustomHeaders()).containsEntry("X-Api-Key", "k");
        assertThat(result.getBody()).isEqualTo("{}");
        assertThat(result.getRps()).isEqualTo(30.0);
        assertThat(result.getNotes()).isEqualTo("updated note");
        verify(targetRepository, never()).save(ArgumentMatchers.any(Target.class));
    }

    @Test
    void updateThrowsWhenTargetMissing() {
        when(targetRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> targetService.update(99L, "n", "d", "/p", HttpMethod.GET,
                TargetType.REST, Map.of(), null, null, null))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deleteDelegatesToRepository() {
        targetService.delete(1L);

        verify(targetRepository).deleteById(1L);
    }
}
