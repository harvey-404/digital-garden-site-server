package com.harvey.digitalgarden.controller;

import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.dto.LikeRequest;
import com.harvey.digitalgarden.dto.LikeResponse;
import com.harvey.digitalgarden.service.LikeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts/{postId}/like")
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    @PostMapping
    public Result<LikeResponse> like(@PathVariable Long postId,
                                     @Valid @RequestBody LikeRequest req,
                                     HttpServletRequest request) {
        String ip = resolveIp(request);
        return Result.success(likeService.like(postId, req.getVisitorId(), ip));
    }

    private String resolveIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
