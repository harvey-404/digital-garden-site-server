package com.harvey.digitalgarden.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "ledger_user")
@SQLRestriction("is_deleted = 0")
public class LedgerUser extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_sn", nullable = false, length = 64)
    private String userSn = "";

    @Column(name = "invite_code", nullable = false, length = 14)
    private String inviteCode = "";

    @Column(name = "display_name", nullable = false, length = 64)
    private String displayName = "";

    @Column(name = "default_budget_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal defaultBudgetAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 16)
    private String status = "active";
}
