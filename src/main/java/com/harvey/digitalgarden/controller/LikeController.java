package com.harvey.digitalgarden.controller;

import com.harvey.digitalgarden.common.Result;
import com.harvey.digitalgarden.dto.LikeRequest;
import com.harvey.digitalgarden.dto.LikeResponse;
import com.harvey.digitalgarden.service.LikeService;
import com.harvey.digitalgarden.service.PostResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts/{postKey}/like")
public class LikeController {

    private final LikeService likeService;
    private final PostResolver postResolver;

    public LikeController(LikeService likeService, PostResolver postResolver) {
        this.likeService = likeService;
        this.postResolver = postResolver;
    }

    @PostMapping
    public Result<LikeResponse> like(@PathVariable String postKey,
                                     @Valid @RequestBody LikeRequest req,
                                     HttpServletRequest request) {
        String ip = resolveIp(request);
        Long postId = postResolver.resolvePublishedId(postKey);
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
