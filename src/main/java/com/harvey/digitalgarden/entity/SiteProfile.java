package com.harvey.digitalgarden.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "site_profile")
public class SiteProfile {
    @Id
    private Long id = 1L; // 单条记录，固定 id=1

    private String displayName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String bio;

    @Column(name = "social_links", length = 1000)
    private String socialLinks; // JSON 字符串：[{"name":"GitHub","url":"..."}]
}
