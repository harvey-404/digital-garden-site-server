package com.harvey.digitalgarden.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TodoRequest {
    @NotBlank(message = "标题不能为空")
    private String title;

    @NotBlank(message = "slug 不能为空")
    private String slug;

    private String description = "";
    private String planMd = "";
    private String priority = "MEDIUM";
    private String status = "DRAFT";

    @Min(0)
    @Max(100)
    private Integer progress = 0;

    private Integer sortOrder = 0;
}
