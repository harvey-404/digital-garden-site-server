package com.harvey.digitalgarden.service;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.dto.ProjectRequest;
import com.harvey.digitalgarden.dto.ProjectVO;
import com.harvey.digitalgarden.entity.Project;
import com.harvey.digitalgarden.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository repository;

    public ProjectService(ProjectRepository repository) {
        this.repository = repository;
    }

    public List<ProjectVO> listAll() {
        return repository.findAllByOrderBySortOrderAscCreatedAtDesc()
                .stream().map(this::toVO).toList();
    }

    public ProjectVO create(ProjectRequest req) {
        Project p = new Project();
        apply(p, req);
        return toVO(repository.save(p));
    }

    public ProjectVO update(Long id, ProjectRequest req) {
        Project p = repository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("项目不存在"));
        apply(p, req);
        return toVO(repository.save(p));
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw BusinessException.notFound("项目不存在");
        }
        repository.deleteById(id);
    }

    private void apply(Project p, ProjectRequest req) {
        p.setTitle(req.getTitle());
        p.setDescription(req.getDescription());
        p.setCoverImage(req.getCoverImage());
        p.setProjectUrl(req.getProjectUrl());
        p.setRepoUrl(req.getRepoUrl());
        p.setTechStack(req.getTechStack());
        p.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
    }

    private ProjectVO toVO(Project p) {
        ProjectVO vo = new ProjectVO();
        vo.setId(p.getId());
        vo.setTitle(p.getTitle());
        vo.setDescription(p.getDescription());
        vo.setCoverImage(p.getCoverImage());
        vo.setProjectUrl(p.getProjectUrl());
        vo.setRepoUrl(p.getRepoUrl());
        vo.setTechStack(p.getTechStack());
        vo.setSortOrder(p.getSortOrder());
        vo.setCreatedAt(p.getCreatedAt());
        return vo;
    }
}
