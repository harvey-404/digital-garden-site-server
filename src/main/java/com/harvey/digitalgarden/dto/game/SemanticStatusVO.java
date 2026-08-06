package com.harvey.digitalgarden.dto.game;

import lombok.Data;

@Data
public class SemanticStatusVO {
    private String status;
    private Long roundId;
    private Boolean guessable;
}
