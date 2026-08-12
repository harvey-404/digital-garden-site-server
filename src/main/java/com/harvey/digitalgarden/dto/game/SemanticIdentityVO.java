package com.harvey.digitalgarden.dto.game;

import lombok.Data;

@Data
public class SemanticIdentityVO {
    /** Last bound nickname for this device fp; empty if unknown. */
    private String username = "";
}
