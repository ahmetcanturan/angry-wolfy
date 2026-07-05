package com.buddywolfy.angrywolfy.repository;

import com.buddywolfy.angrywolfy.entity.Config;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConfigRepository extends JpaRepository<Config, Long> {

    List<Config> findByProjectId(Long projectId);
}
