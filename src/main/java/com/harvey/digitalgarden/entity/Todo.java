package com.harvey.digitalgarden.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "todo")
@SQLRestriction("is_deleted = 0")
public class Todo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title = "";

    @Column(nullable = false, length = 200)
    private String slug = "";

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description = "";

    @Lob
    @Column(name = "plan_md", nullable = false, columnDefinition = "LONGTEXT")
    private String planMd = "";

    @Column(nullable = false, length = 16)
    private String priority = "MEDIUM";

    @Column(nullable = false, columnDefinition = "TINYINT UNSIGNED NOT NULL DEFAULT 0")
    private Integer progress = 0;

    @Column(nullable = false, length = 16)
    private String status = "DRAFT";

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
