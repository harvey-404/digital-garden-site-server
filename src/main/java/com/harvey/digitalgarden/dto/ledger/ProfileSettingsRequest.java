package com.harvey.digitalgarden.dto.ledger;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProfileSettingsRequest {
    @NotBlank(message = "昵称不能为空")
    @Size(max = 64, message = "昵称长度不能超过 64")
    private String displayName;
}
