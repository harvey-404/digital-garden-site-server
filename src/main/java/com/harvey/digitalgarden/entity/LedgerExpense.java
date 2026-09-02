package com.harvey.digitalgarden.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "ledger_expense")
@SQLRestriction("is_deleted = 0")
public class LedgerExpense extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId = 0L;

    @Column(name = "budget_id", nullable = false)
    private Long budgetId = 0L;

    @Column(name = "category_id", nullable = false)
    private Long categoryId = 0L;

    @Column(nullable = false, length = 500)
    private String description = "";

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "recorded_at", nullable = false)
    private Long recordedAt = 0L;
}
