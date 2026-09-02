package com.harvey.digitalgarden.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "ledger_budget")
@SQLRestriction("is_deleted = 0")
public class LedgerBudget extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId = 0L;

    @Column(name = "budget_dtm", nullable = false)
    private Long budgetDtm = 0L;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;
}
