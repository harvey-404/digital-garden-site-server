package com.harvey.digitalgarden.service;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.dto.LikeResponse;
import com.harvey.digitalgarden.entity.Post;
import com.harvey.digitalgarden.entity.PostLike;
import com.harvey.digitalgarden.repository.PostLikeRepository;
import com.harvey.digitalgarden.repository.PostRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class LikeService {

    private final PostLikeRepository likeRepository;
    private final PostRepository postRepository;

    public LikeService(PostLikeRepository likeRepository, PostRepository postRepository) {
        this.likeRepository = likeRepository;
        this.postRepository = postRepository;
    }

    @Transactional
    public LikeResponse like(Long postId, String visitorId, String ip) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> BusinessException.notFound("文章不存在"));

        if (likeRepository.existsByPostIdAndVisitorId(postId, visitorId)) {
            return new LikeResponse(post.getLikeCount(), true);
        }
        try {
            PostLike like = new PostLike();
            like.setPostId(postId);
            like.setVisitorId(visitorId);
            like.setIpHash(sha256(ip));
            likeRepository.save(like);
            post.setLikeCount(post.getLikeCount() + 1);
            postRepository.save(post);
            return new LikeResponse(post.getLikeCount(), true);
        } catch (DataIntegrityViolationException e) {
            // 并发下唯一约束兜底：已点过，直接返回当前计数
            return new LikeResponse(post.getLikeCount(), true);
        }
    }

    private String sha256(String input) {
        if (input == null) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
