package com.harvey.digitalgarden.repository.projection;

import java.math.BigDecimal;

public interface CategoryAmountSum {
    Long getCategoryId();

    BigDecimal getAmount();
}
