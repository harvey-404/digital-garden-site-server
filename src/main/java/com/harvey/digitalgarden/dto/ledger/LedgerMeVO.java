package com.harvey.digitalgarden.dto.ledger;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class LedgerMeVO {
    private String userSn;
    private String displayName;
    private BigDecimal defaultBudgetAmount;
    private String status;
    private boolean onboardingDone;
}
