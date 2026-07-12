package com.harvey.digitalgarden.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "site_profile")
@SQLRestriction("is_deleted = 0")
public class SiteProfile extends BaseEntity {
    @Id
    private Long id = 1L;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName = "";

    @Column(name = "avatar_url", nullable = false, length = 255)
    private String avatarUrl = "";

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String bio = "";

    @Column(name = "social_links", nullable = false, length = 1000)
    private String socialLinks = "[]";
}
