package com.harvey.digitalgarden.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "game_device_identity")
@SQLRestriction("is_deleted = 0")
public class GameDeviceIdentity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String fp = "";

    @Column(nullable = false, length = 16)
    private String username = "";

    /** Unix seconds when username was last accepted/bound. */
    @Column(name = "bound_at", nullable = false)
    private Long boundAt = 0L;
}
