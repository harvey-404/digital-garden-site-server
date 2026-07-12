package com.harvey.digitalgarden.service;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.common.PageResult;
import com.harvey.digitalgarden.dto.TodoDetailVO;
import com.harvey.digitalgarden.dto.TodoRequest;
import com.harvey.digitalgarden.dto.TodoVO;
import com.harvey.digitalgarden.entity.Todo;
import com.harvey.digitalgarden.repository.TodoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service
public class TodoService {

    private final TodoRepository todoRepository;

    public TodoService(TodoRepository todoRepository) {
        this.todoRepository = todoRepository;
    }

    private Pageable adminPageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "inDtm"));
    }

    public PageResult<TodoVO> listPublished(int page, int size, String priority, Integer minProgress, Integer maxProgress) {
        int min = minProgress == null ? 0 : minProgress;
        int max = maxProgress == null ? 100 : maxProgress;
        String p = (priority == null || priority.isBlank()) ? null : priority;
        Page<Todo> result = todoRepository.findPublishedFiltered(p, min, max, PageRequest.of(page, size));
        List<TodoVO> items = result.getContent().stream().map(this::toVO).toList();
        return PageResult.of(result, items);
    }

    public TodoDetailVO getPublishedBySlug(String slug) {
        Todo todo = todoRepository.findBySlug(slug)
                .orElseThrow(() -> BusinessException.notFound("计划不存在"));
        if (!"PUBLISHED".equals(todo.getStatus())) {
            throw BusinessException.notFound("计划不存在");
        }
        return toDetailVO(todo);
    }

    public PageResult<TodoVO> listAll(int page, int size) {
        Page<Todo> result = todoRepository.findAll(adminPageable(page, size));
        List<TodoVO> items = result.getContent().stream().map(this::toVO).toList();
        return PageResult.of(result, items);
    }

    public TodoDetailVO getById(Long id) {
        Todo todo = todoRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("计划不存在"));
        return toDetailVO(todo);
    }

    @Transactional
    public TodoDetailVO create(TodoRequest req) {
        if (todoRepository.existsBySlug(req.getSlug())) {
            throw BusinessException.conflict("slug 已存在");
        }
        Todo todo = new Todo();
        apply(todo, req);
        return toDetailVO(todoRepository.save(todo));
    }

    @Transactional
    public TodoDetailVO update(Long id, TodoRequest req) {
        Todo todo = todoRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("计划不存在"));
        if (!todo.getSlug().equals(req.getSlug()) && todoRepository.existsBySlug(req.getSlug())) {
            throw BusinessException.conflict("slug 已存在");
        }
        apply(todo, req);
        return toDetailVO(todoRepository.save(todo));
    }

    @Transactional
    public void delete(Long id) {
        Todo todo = todoRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("计划不存在"));
        todo.setIsDeleted(true);
        todo.setUpdateDtm(Instant.now().getEpochSecond());
        todoRepository.save(todo);
    }

    private void apply(Todo todo, TodoRequest req) {
        todo.setTitle(req.getTitle());
        todo.setSlug(req.getSlug());
        todo.setDescription(req.getDescription() == null ? "" : req.getDescription());
        todo.setPlanMd(req.getPlanMd() == null ? "" : req.getPlanMd());
        todo.setPriority(normalizePriority(req.getPriority()));
        todo.setProgress(req.getProgress() == null ? 0 : req.getProgress());
        todo.setStatus(normalizeStatus(req.getStatus()));
        todo.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
    }

    private String normalizePriority(String priority) {
        if (priority == null) return "MEDIUM";
        return switch (priority.toUpperCase()) {
            case "HIGH", "MEDIUM", "LOW" -> priority.toUpperCase();
            default -> "MEDIUM";
        };
    }

    private String normalizeStatus(String status) {
        if (status == null) return "DRAFT";
        return "PUBLISHED".equalsIgnoreCase(status) ? "PUBLISHED" : "DRAFT";
    }

    private TodoVO toVO(Todo todo) {
        TodoVO vo = new TodoVO();
        vo.setId(todo.getId());
        vo.setTitle(todo.getTitle());
        vo.setSlug(todo.getSlug());
        vo.setDescription(todo.getDescription());
        vo.setPriority(todo.getPriority());
        vo.setProgress(todo.getProgress());
        vo.setStatus(todo.getStatus());
        vo.setSortOrder(todo.getSortOrder());
        vo.setInDtm(todo.getInDtm());
        return vo;
    }

    private TodoDetailVO toDetailVO(Todo todo) {
        TodoDetailVO vo = new TodoDetailVO();
        TodoVO base = toVO(todo);
        vo.setId(base.getId());
        vo.setTitle(base.getTitle());
        vo.setSlug(base.getSlug());
        vo.setDescription(base.getDescription());
        vo.setPriority(base.getPriority());
        vo.setProgress(base.getProgress());
        vo.setStatus(base.getStatus());
        vo.setSortOrder(base.getSortOrder());
        vo.setInDtm(base.getInDtm());
        vo.setPlanMd(todo.getPlanMd());
        vo.setUpdateDtm(todo.getUpdateDtm());
        return vo;
    }
}
