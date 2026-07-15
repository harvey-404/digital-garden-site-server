package com.harvey.digitalgarden.controller;

import com.harvey.digitalgarden.common.PageResult;
import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.dto.PostDetailVO;
import com.harvey.digitalgarden.dto.PostRequest;
import com.harvey.digitalgarden.dto.PostVO;
import com.harvey.digitalgarden.service.PostService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/posts")
public class AdminPostController {

    private final PostService postService;

    public AdminPostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    public Result<PageResult<PostVO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(postService.listAll(page, size));
    }

    @GetMapping("/{id}")
    public Result<PostDetailVO> detail(@PathVariable Long id) {
        return Result.success(postService.getById(id));
    }

    @PostMapping
    public Result<PostDetailVO> create(@Valid @RequestBody PostRequest req) {
        return Result.success(postService.create(req));
    }

    @PutMapping("/{id}")
    public Result<PostDetailVO> update(@PathVariable Long id, @Valid @RequestBody PostRequest req) {
        return Result.success(postService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        postService.delete(id);
        return Result.success();
    }
}
