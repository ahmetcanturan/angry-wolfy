package com.buddywolfy.angrywolfy.web.view;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ProjectForm {

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 2000)
    private String description;

    @Size(max = 500)
    private String repositoryPath;

    public ProjectForm() {
    }

    public ProjectForm(String name, String description, String repositoryPath) {
        this.name = name;
        this.description = description;
        this.repositoryPath = repositoryPath;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRepositoryPath() {
        return repositoryPath;
    }

    public void setRepositoryPath(String repositoryPath) {
        this.repositoryPath = repositoryPath;
    }
}
