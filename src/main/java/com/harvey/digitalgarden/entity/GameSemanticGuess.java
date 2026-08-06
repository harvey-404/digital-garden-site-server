package com.harvey.digitalgarden.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "game_semantic_guess")
@SQLRestriction("is_deleted = 0")
public class GameSemanticGuess extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "round_id", nullable = false)
    private Long roundId = 0L;

    @Column(nullable = false, length = 64)
    private String word = "";

    @Column(nullable = false)
    private Double score = 0.0;

    @Column(name = "first_username", nullable = false, length = 32)
    private String firstUsername = "";

    @Column(name = "first_fp", nullable = false, length = 64)
    private String firstFp = "";
}
