package com.harvey.digitalgarden.repository;

import com.harvey.digitalgarden.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByPostIdAndStatusOrderByCreatedAtDesc(Long postId, String status);
    List<Comment> findByStatusOrderByCreatedAtDesc(String status);
}
