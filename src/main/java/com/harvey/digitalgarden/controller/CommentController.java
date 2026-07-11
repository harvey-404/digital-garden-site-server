package com.harvey.digitalgarden.controller;

import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.dto.CommentRequest;
import com.harvey.digitalgarden.dto.CommentVO;
import com.harvey.digitalgarden.service.CommentService;
import com.harvey.digitalgarden.service.PostResolver;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/posts/{postKey}/comments")
public class CommentController {

    private final CommentService commentService;
    private final PostResolver postResolver;

    public CommentController(CommentService commentService, PostResolver postResolver) {
        this.commentService = commentService;
        this.postResolver = postResolver;
    }

    @GetMapping
    public Result<List<CommentVO>> list(@PathVariable String postKey) {
        Long postId = postResolver.resolvePublishedId(postKey);
        return Result.success(commentService.listApproved(postId));
    }

    @PostMapping
    public Result<CommentVO> submit(@PathVariable String postKey, @Valid @RequestBody CommentRequest req) {
        Long postId = postResolver.resolvePublishedId(postKey);
        return Result.success(commentService.submit(postId, req));
    }
}
