package com.harvey.digitalgarden.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "comment")
@SQLRestriction("is_deleted = 0")
public class Comment extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", nullable = false)
    private Long postId = 0L;

    @Column(nullable = false, length = 64)
    private String nickname = "";

    @Column(nullable = false, length = 1000)
    private String content = "";

    @Column(nullable = false, length = 16)
    private String status = "PENDING";
}
