package com.buddywolfy.angrywolfy.service;

import com.buddywolfy.angrywolfy.entity.Project;
import com.buddywolfy.angrywolfy.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;

    public ProjectService(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @Transactional
    public Project create(String name, String description, String repositoryPath) {
        return projectRepository.save(new Project(name, description, repositoryPath));
    }

    @Transactional(readOnly = true)
    public Project getById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Project not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Project> getAll() {
        return projectRepository.findAll();
    }

    @Transactional
    public Project update(Long id, String name, String description, String repositoryPath) {
        Project project = getById(id);
        project.setName(name);
        project.setDescription(description);
        project.setRepositoryPath(repositoryPath);
        return project;
    }

    @Transactional
    public void delete(Long id) {
        projectRepository.deleteById(id);
    }
}
