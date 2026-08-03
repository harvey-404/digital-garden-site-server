package com.harvey.digitalgarden.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AlbumVideoRequest {
    @NotBlank(message = "视频链接不能为空")
    private String url;

    private String platform = "other";
    private String title = "";
    private Integer sortOrder = 0;
}
