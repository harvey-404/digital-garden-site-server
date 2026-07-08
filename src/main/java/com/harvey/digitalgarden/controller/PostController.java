package com.harvey.digitalgarden.controller;

import com.harvey.digitalgarden.common.PageResult;
import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.dto.PostDetailVO;
import com.harvey.digitalgarden.dto.PostVO;
import com.harvey.digitalgarden.service.PostService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    public Result<PageResult<PostVO>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String tag) {
        return Result.success(postService.listPublished(page, size, tag));
    }

    @GetMapping("/search")
    public Result<PageResult<PostVO>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(postService.search(q, page, size));
    }

    @GetMapping("/{slug}")
    public Result<PostDetailVO> detail(@PathVariable String slug) {
        return Result.success(postService.getPublishedBySlug(slug));
    }
}
