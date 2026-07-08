package com.harvey.digitalgarden.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ProjectRequest {
    @NotBlank(message = "项目名不能为空")
    private String title;
    private String description;
    private String coverImage;
    private String projectUrl;
    private String repoUrl;
    private String techStack;
    private Integer sortOrder = 0;
}
