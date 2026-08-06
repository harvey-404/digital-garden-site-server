package com.harvey.digitalgarden.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.SQLRestriction;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "game_semantic_round")
@SQLRestriction("is_deleted = 0")
public class GameSemanticRound extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_word", nullable = false, length = 64)
    private String targetWord = "";

    @Column(name = "word_id", nullable = false)
    private Long wordId = 0L;

    @Column(nullable = false, length = 20)
    private String status = "finished"; // active|finished|waiting_words

    @Column(name = "winner_username", nullable = false, length = 32)
    private String winnerUsername = "";

    @Column(name = "winner_fp", nullable = false, length = 64)
    private String winnerFp = "";

    @Column(name = "started_at", nullable = false)
    private Long startedAt = 0L;

    @Column(name = "finished_at", nullable = false)
    private Long finishedAt = 0L;
}
