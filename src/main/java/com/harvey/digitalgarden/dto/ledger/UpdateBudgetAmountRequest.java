package com.harvey.digitalgarden.dto.ledger;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class UpdateBudgetAmountRequest {
    @NotNull(message = "amount 不能为空")
    private BigDecimal amount;
}
