package com.harvey.digitalgarden.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AlbumPhotoRequest {
    @NotBlank(message = "图片地址不能为空")
    private String fileUrl;

    private String caption = "";
    private Integer sortOrder = 0;
}
