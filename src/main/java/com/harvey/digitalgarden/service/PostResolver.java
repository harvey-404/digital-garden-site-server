package com.harvey.digitalgarden.service;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.entity.Post;
import com.harvey.digitalgarden.repository.PostRepository;
import org.springframework.stereotype.Service;

@Service
public class PostResolver {

    private final PostRepository postRepository;

    public PostResolver(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    /**
     * 解析文章标识：支持数字 ID 或 slug（与 GET /api/posts/{slug} 一致）。
     */
    public Post resolvePublished(String slugOrId) {
        Post post = resolveRaw(slugOrId);
        if (!"PUBLISHED".equals(post.getStatus())) {
            throw BusinessException.notFound("文章不存在");
        }
        return post;
    }

    public Long resolvePublishedId(String slugOrId) {
        return resolvePublished(slugOrId).getId();
    }

    private Post resolveRaw(String slugOrId) {
        if (slugOrId == null || slugOrId.isBlank()) {
            throw BusinessException.notFound("文章不存在");
        }
        try {
            Long id = Long.parseLong(slugOrId);
            return postRepository.findById(id)
                    .orElseThrow(() -> BusinessException.notFound("文章不存在"));
        } catch (NumberFormatException ignored) {
            return postRepository.findBySlug(slugOrId)
                    .orElseThrow(() -> BusinessException.notFound("文章不存在"));
        }
    }
}
