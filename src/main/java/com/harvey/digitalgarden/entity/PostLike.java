package com.harvey.digitalgarden.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "post_like",
        uniqueConstraints = @UniqueConstraint(name = "uk_post_visitor",
                columnNames = {"post_id", "visitor_id"}))
public class PostLike {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "visitor_id", nullable = false, length = 128)
    private String visitorId;

    @Column(name = "ip_hash", length = 128)
    private String ipHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
