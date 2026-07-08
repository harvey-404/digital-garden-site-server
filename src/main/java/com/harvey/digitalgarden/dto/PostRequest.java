package com.harvey.digitalgarden.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.HashSet;
import java.util.Set;

@Data
public class PostRequest {
    @NotBlank(message = "标题不能为空")
    private String title;
    @NotBlank(message = "slug 不能为空")
    private String slug;
    @NotBlank(message = "正文不能为空")
    private String contentMd;
    private String summary;
    private String coverImage;
    private String status = "DRAFT"; // DRAFT / PUBLISHED
    private Set<String> tags = new HashSet<>();
}
