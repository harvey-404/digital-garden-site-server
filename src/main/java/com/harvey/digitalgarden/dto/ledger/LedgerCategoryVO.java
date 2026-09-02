package com.harvey.digitalgarden.dto.ledger;

import lombok.Data;

@Data
public class LedgerCategoryVO {
    private Long id;
    private String name;
    private String icon;
    private Integer sortOrder;
}
