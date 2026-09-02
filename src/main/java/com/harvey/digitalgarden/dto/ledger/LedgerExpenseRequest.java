package com.harvey.digitalgarden.dto.ledger;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class LedgerExpenseRequest {
    private Long categoryId;
    private String description;
    private BigDecimal amount;
    private Long recordedAt;
}
