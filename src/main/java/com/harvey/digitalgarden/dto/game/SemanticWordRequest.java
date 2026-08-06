package com.harvey.digitalgarden.dto.game;

import lombok.Data;

@Data
public class SemanticWordRequest {
    private String word;
    private Integer queueOrder;
}
