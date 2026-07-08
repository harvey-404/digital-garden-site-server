package com.harvey.digitalgarden.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "comment")
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(nullable = false, length = 64)
    private String nickname;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(nullable = false, length = 16)
    private String status = "PENDING"; // PENDING / APPROVED

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
