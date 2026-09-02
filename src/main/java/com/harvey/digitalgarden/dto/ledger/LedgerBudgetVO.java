package com.harvey.digitalgarden.dto.ledger;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class LedgerBudgetVO {
    private Long id;
    private Long budgetDtm;
    private BigDecimal amount;
    private BigDecimal spent;
    private BigDecimal rate;
    /** Present when spent == 0; value 「暂无消费」. */
    private String spentLabel;
}
