package com.harvey.digitalgarden.dto.ledger;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class DefaultBudgetRequest {
    @NotNull(message = "默认月预算不能为空")
    private BigDecimal defaultBudgetAmount;
}
