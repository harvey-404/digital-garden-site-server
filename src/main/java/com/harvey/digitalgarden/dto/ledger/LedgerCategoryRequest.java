package com.harvey.digitalgarden.dto.ledger;

import lombok.Data;

@Data
public class LedgerCategoryRequest {
    private String name;
    private String icon;
    private Integer sortOrder;
}
