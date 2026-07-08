package com.harvey.digitalgarden.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LikeRequest {
    @NotBlank(message = "visitorId 不能为空")
    private String visitorId;
}
