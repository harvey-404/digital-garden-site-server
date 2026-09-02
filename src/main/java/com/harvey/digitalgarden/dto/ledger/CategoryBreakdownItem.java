package com.harvey.digitalgarden.dto.ledger;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class CategoryBreakdownItem {
    private Long categoryId;
    private String categoryName;
    private BigDecimal amount;
    private BigDecimal rate;
}
