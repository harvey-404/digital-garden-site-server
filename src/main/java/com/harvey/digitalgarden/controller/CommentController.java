package com.harvey.digitalgarden.controller;

import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.dto.CommentRequest;
import com.harvey.digitalgarden.dto.CommentVO;
import com.harvey.digitalgarden.service.CommentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/posts/{postId}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    public Result<List<CommentVO>> list(@PathVariable Long postId) {
        return Result.success(commentService.listApproved(postId));
    }

    @PostMapping
    public Result<CommentVO> submit(@PathVariable Long postId, @Valid @RequestBody CommentRequest req) {
        return Result.success(commentService.submit(postId, req));
    }
}
