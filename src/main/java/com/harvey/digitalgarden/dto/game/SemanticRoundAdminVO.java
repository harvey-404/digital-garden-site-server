package com.harvey.digitalgarden.dto.game;

import lombok.Data;

@Data
public class SemanticRoundAdminVO {
    private String status;
    private Long roundId;
    private String targetWord;
    private Long guessCount;
    private Long pendingCount;
    private Boolean guessable;
}
