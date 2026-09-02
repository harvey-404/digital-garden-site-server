package com.harvey.digitalgarden.dto.ledger;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class LedgerDashboardVO {
    private String month;
    private Long budgetId;
    private Long budgetDtm;
    /** Monthly budget amount when activated; null if month not enabled. */
    private BigDecimal budgetAmount;
    /** User default budget; shown as reference when month not enabled. */
    private BigDecimal defaultBudgetAmount;
    private boolean budgetActivated;
    private BigDecimal spent;
    private BigDecimal rate;
    private BigDecimal remaining;
    private List<CategoryBreakdownItem> categoryBreakdown;
}
