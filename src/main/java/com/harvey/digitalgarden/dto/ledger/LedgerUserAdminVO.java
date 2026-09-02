package com.harvey.digitalgarden.dto.ledger;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class LedgerUserAdminVO {
    private Long id;
    private String userSn;
    private String inviteCode;
    private String displayName;
    private BigDecimal defaultBudgetAmount;
    private String status;
    private Long inDtm;
    private Long updateDtm;
}
