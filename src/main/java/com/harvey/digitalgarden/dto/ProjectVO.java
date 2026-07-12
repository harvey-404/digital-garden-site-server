package com.harvey.digitalgarden.dto;

import lombok.Data;

@Data
public class ProjectVO {
    private Long id;
    private String title;
    private String description;
    private String coverImage;
    private String projectUrl;
    private String repoUrl;
    private String techStack;
    private Integer sortOrder;
    private Long inDtm;
}
