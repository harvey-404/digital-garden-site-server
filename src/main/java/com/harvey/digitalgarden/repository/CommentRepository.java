package com.harvey.digitalgarden.repository;

import com.harvey.digitalgarden.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByPostIdAndStatusOrderByInDtmDesc(Long postId, String status);
    List<Comment> findByStatusOrderByInDtmDesc(String status);

    @Modifying
    @Query("update Comment c set c.isDeleted = true, c.updateDtm = :now where c.postId = :postId and c.isDeleted = false")
    void softDeleteByPostId(@Param("postId") Long postId, @Param("now") Long now);
}
