package com.harvey.digitalgarden.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "project")
@SQLRestriction("is_deleted = 0")
public class Project extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title = "";

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description = "";

    @Column(name = "cover_image", nullable = false, length = 255)
    private String coverImage = "";

    @Column(name = "project_url", nullable = false, length = 255)
    private String projectUrl = "";

    @Column(name = "repo_url", nullable = false, length = 255)
    private String repoUrl = "";

    @Column(name = "tech_stack", nullable = false, length = 255)
    private String techStack = "";

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
