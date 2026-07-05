package com.buddywolfy.angrywolfy.repository;

import com.buddywolfy.angrywolfy.entity.HttpMethod;
import com.buddywolfy.angrywolfy.entity.Project;
import com.buddywolfy.angrywolfy.entity.Target;
import com.buddywolfy.angrywolfy.entity.TargetType;
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
class TargetRepositoryTests {

    @Autowired
    private TargetRepository targetRepository;

    @Autowired
    private ProjectRepository projectRepository;

    private Project persistedProject() {
        return projectRepository.save(new Project("Angrywolfy", "Test project", "/repos/angrywolfy"));
    }

    @Test
    void savesAndReadsBackATargetWithJsonHeaders() {
        Project project = persistedProject();
        Map<String, String> headers = Map.of("Authorization", "Bearer token123");

        Target saved = targetRepository.save(new Target(
                "Get users", "Fetches all users", project, "/api/users", HttpMethod.GET, TargetType.REST,
                headers, null, 50.0, "smoke test target"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        Optional<Target> found = targetRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Get users");
        assertThat(found.get().getPath()).isEqualTo("/api/users");
        assertThat(found.get().getMethod()).isEqualTo(HttpMethod.GET);
        assertThat(found.get().getType()).isEqualTo(TargetType.REST);
        assertThat(found.get().getCustomHeaders()).containsEntry("Authorization", "Bearer token123");
        assertThat(found.get().getRps()).isEqualTo(50.0);
        assertThat(found.get().getNotes()).isEqualTo("smoke test target");
    }

    @Test
    void defaultsMethodAndTypeWhenNotSpecified() {
        Project project = persistedProject();

        Target saved = targetRepository.save(new Target(
                "Default method", null, project, "/api/x", null, null, Map.of(), null, null, null));

        assertThat(saved.getMethod()).isEqualTo(HttpMethod.GET);
        assertThat(saved.getType()).isEqualTo(TargetType.REST);
    }

    @Test
    void savesTargetWithBodyForNonGetMethods() {
        Project project = persistedProject();

        Target saved = targetRepository.save(new Target(
                "Create user", null, project, "/api/users", HttpMethod.POST, TargetType.REST,
                Map.of("Content-Type", "application/json"), "{\"name\":\"test\"}", 10.0, null));

        Target found = targetRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getMethod()).isEqualTo(HttpMethod.POST);
        assertThat(found.getBody()).isEqualTo("{\"name\":\"test\"}");
    }

    @Test
    void savesGraphqlTarget() {
        Project project = persistedProject();

        Target saved = targetRepository.save(new Target(
                "GraphQL query", null, project, "/graphql", HttpMethod.POST, TargetType.GRAPHQL,
                Map.of(), "{\"query\":\"{ users { id } }\"}", null, null));

        Target found = targetRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getType()).isEqualTo(TargetType.GRAPHQL);
    }

    @Test
    void findsTargetsByProjectId() {
        Project project = persistedProject();
        targetRepository.save(new Target("One", null, project, "/a", HttpMethod.GET, TargetType.REST,
                Map.of(), null, null, null));
        targetRepository.save(new Target("Two", null, project, "/b", HttpMethod.POST, TargetType.REST,
                Map.of(), null, null, null));

        List<Target> found = targetRepository.findByProjectId(project.getId());

        assertThat(found).hasSize(2);
    }

    @Test
    void rejectsBlankPath() {
        Project project = persistedProject();

        assertThatThrownBy(() -> targetRepository.saveAndFlush(
                new Target("Bad", null, project, "", HttpMethod.GET, TargetType.REST, Map.of(), null, null, null)))
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void rejectsNonPositiveRps() {
        Project project = persistedProject();

        assertThatThrownBy(() -> targetRepository.saveAndFlush(
                new Target("Bad rps", null, project, "/a", HttpMethod.GET, TargetType.REST, Map.of(), null, 0.0, null)))
                .isInstanceOf(ConstraintViolationException.class);
    }
}
