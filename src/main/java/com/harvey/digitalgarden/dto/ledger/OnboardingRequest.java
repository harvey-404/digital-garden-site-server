package com.harvey.digitalgarden.dto.ledger;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class OnboardingRequest {
    @NotBlank(message = "昵称不能为空")
    @Size(max = 64, message = "昵称长度不能超过 64")
    private String displayName;

    @NotNull(message = "默认月预算不能为空")
    private BigDecimal defaultBudgetAmount;
}
