package com.buddywolfy.angrywolfy.repository;

import com.buddywolfy.angrywolfy.entity.Project;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ProjectRepositoryTests {

    @org.springframework.beans.factory.annotation.Autowired
    private ProjectRepository projectRepository;

    @Test
    void savesAndReadsBackAProject() {
        Project saved = projectRepository.save(
                new Project("Angrywolfy", "Test project", "/repos/angrywolfy"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        Optional<Project> found = projectRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Angrywolfy");
        assertThat(found.get().getRepositoryPath()).isEqualTo("/repos/angrywolfy");
    }

    @Test
    void rejectsNameLongerThan255Characters() {
        String tooLong = "a".repeat(256);

        assertThatThrownBy(() -> projectRepository.saveAndFlush(
                new Project(tooLong, "desc", "/repos/x")))
                .isInstanceOf(ConstraintViolationException.class);
    }
}
