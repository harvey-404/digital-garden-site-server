package com.harvey.digitalgarden.dto.ledger;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ActivateBudgetRequest {
    @NotBlank(message = "month 不能为空")
    private String month;
}
