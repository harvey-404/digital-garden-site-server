package com.harvey.digitalgarden.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "ledger_category")
@SQLRestriction("is_deleted = 0")
public class LedgerCategory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId = 0L;

    @Column(nullable = false, length = 64)
    private String name = "";

    @Column(nullable = false, length = 32)
    private String icon = "";

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
