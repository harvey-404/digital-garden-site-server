package com.harvey.digitalgarden.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "game_semantic_word")
@SQLRestriction("is_deleted = 0")
public class GameSemanticWord extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String word = "";

    @Column(name = "queue_order", nullable = false)
    private Integer queueOrder = 0;

    @Column(nullable = false, length = 20)
    private String status = "pending"; // pending|active|used|skipped

    /** Weakest progressive hint; public after 10 unique guesses. */
    @Column(nullable = false, length = 256)
    private String hint1 = "";

    /** Medium progressive hint; public after 20 unique guesses. */
    @Column(nullable = false, length = 256)
    private String hint2 = "";

    /** Strongest progressive hint; public after 30 unique guesses. */
    @Column(nullable = false, length = 256)
    private String hint3 = "";
}
