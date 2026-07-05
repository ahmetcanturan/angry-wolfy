package com.buddywolfy.angrywolfy.dto;

import com.buddywolfy.angrywolfy.entity.Project;

import java.time.Instant;

public record ProjectResponse(
        Long id,
        String name,
        String description,
        String repositoryPath,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getRepositoryPath(),
                project.getCreatedAt(),
                project.getUpdatedAt()
        );
    }
}
