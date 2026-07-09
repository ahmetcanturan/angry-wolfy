package com.buddywolfy.angrywolfy.repository;

import com.buddywolfy.angrywolfy.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    /** The 5 most recently created projects, newest first — for the dashboard rollup. */
    List<Project> findTop5ByOrderByCreatedAtDesc();
}
