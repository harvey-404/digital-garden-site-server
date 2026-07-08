package com.harvey.digitalgarden.repository;

import com.harvey.digitalgarden.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    boolean existsByPostIdAndVisitorId(Long postId, String visitorId);
}
