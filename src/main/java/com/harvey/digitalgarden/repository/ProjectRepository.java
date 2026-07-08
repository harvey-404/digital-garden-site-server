package com.harvey.digitalgarden.repository;

import com.harvey.digitalgarden.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findAllByOrderBySortOrderAscCreatedAtDesc();
}
