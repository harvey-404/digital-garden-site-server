package com.harvey.digitalgarden.controller;

import com.harvey.digitalgarden.common.PageResult;
import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.dto.TodoDetailVO;
import com.harvey.digitalgarden.dto.TodoRequest;
import com.harvey.digitalgarden.dto.TodoVO;
import com.harvey.digitalgarden.service.TodoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/todos")
public class AdminTodoController {

    private final TodoService todoService;

    public AdminTodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    @GetMapping
    public Result<PageResult<TodoVO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(todoService.listAll(page, size));
    }

    @GetMapping("/{id}")
    public Result<TodoDetailVO> detail(@PathVariable Long id) {
        return Result.success(todoService.getById(id));
    }

    @PostMapping
    public Result<TodoDetailVO> create(@Valid @RequestBody TodoRequest req) {
        return Result.success(todoService.create(req));
    }

    @PutMapping("/{id}")
    public Result<TodoDetailVO> update(@PathVariable Long id, @Valid @RequestBody TodoRequest req) {
        return Result.success(todoService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        todoService.delete(id);
        return Result.success();
    }
}
