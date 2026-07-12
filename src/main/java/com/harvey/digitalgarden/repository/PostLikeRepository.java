package com.harvey.digitalgarden.repository;

import com.harvey.digitalgarden.entity.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    boolean existsByPostIdAndVisitorId(Long postId, String visitorId);

    @Modifying
    @Query("update PostLike pl set pl.isDeleted = true, pl.updateDtm = :now where pl.postId = :postId and pl.isDeleted = false")
    void softDeleteByPostId(@Param("postId") Long postId, @Param("now") Long now);
}
