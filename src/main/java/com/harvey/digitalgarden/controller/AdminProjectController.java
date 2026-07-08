package com.harvey.digitalgarden.controller;

import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.dto.ProjectRequest;
import com.harvey.digitalgarden.dto.ProjectVO;
import com.harvey.digitalgarden.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/projects")
public class AdminProjectController {

    private final ProjectService projectService;

    public AdminProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public Result<ProjectVO> create(@Valid @RequestBody ProjectRequest req) {
        return Result.success(projectService.create(req));
    }

    @PutMapping("/{id}")
    public Result<ProjectVO> update(@PathVariable Long id, @Valid @RequestBody ProjectRequest req) {
        return Result.success(projectService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        projectService.delete(id);
        return Result.success();
    }
}
