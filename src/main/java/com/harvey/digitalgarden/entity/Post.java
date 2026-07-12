package com.harvey.digitalgarden.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "post")
@SQLRestriction("is_deleted = 0")
public class Post extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title = "";

    @Column(nullable = false, unique = true, length = 200)
    private String slug = "";

    @Column(name = "content_md", nullable = false, columnDefinition = "LONGTEXT")
    private String contentMd = "";

    @Column(nullable = false, length = 500)
    private String summary = "";

    @Column(name = "cover_image", nullable = false, length = 255)
    private String coverImage = "";

    @Column(nullable = false, length = 16)
    private String status = "DRAFT";

    @Column(name = "view_count", nullable = false)
    private Integer viewCount = 0;

    @Column(name = "like_count", nullable = false)
    private Integer likeCount = 0;
}
