package com.harvey.digitalgarden.controller;

import com.harvey.digitalgarden.common.PageResult;
import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.dto.TodoDetailVO;
import com.harvey.digitalgarden.dto.TodoVO;
import com.harvey.digitalgarden.service.TodoService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/todos")
public class TodoController {

    private final TodoService todoService;

    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    @GetMapping
    public Result<PageResult<TodoVO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Integer minProgress,
            @RequestParam(required = false) Integer maxProgress) {
        return Result.success(todoService.listPublished(page, size, priority, minProgress, maxProgress));
    }

    @GetMapping("/{slug}")
    public Result<TodoDetailVO> detail(@PathVariable String slug) {
        return Result.success(todoService.getPublishedBySlug(slug));
    }
}
