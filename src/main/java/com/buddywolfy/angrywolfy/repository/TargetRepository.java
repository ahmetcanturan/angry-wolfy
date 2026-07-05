package com.buddywolfy.angrywolfy.repository;

import com.buddywolfy.angrywolfy.entity.Target;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TargetRepository extends JpaRepository<Target, Long> {

    List<Target> findByProjectId(Long projectId);
}
