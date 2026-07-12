package com.harvey.digitalgarden.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "admin_user")
@SQLRestriction("is_deleted = 0")
public class AdminUser extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username = "";

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash = "";
}
