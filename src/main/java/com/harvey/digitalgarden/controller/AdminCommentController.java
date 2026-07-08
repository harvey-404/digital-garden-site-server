package com.harvey.digitalgarden.controller;

import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.dto.CommentVO;
import com.harvey.digitalgarden.service.CommentService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/comments")
public class AdminCommentController {

    private final CommentService commentService;

    public AdminCommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    public Result<List<CommentVO>> list(@RequestParam(defaultValue = "PENDING") String status) {
        return Result.success(commentService.listByStatus(status));
    }

    @PutMapping("/{id}/approve")
    public Result<CommentVO> approve(@PathVariable Long id) {
        return Result.success(commentService.approve(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return Result.success();
    }
}
