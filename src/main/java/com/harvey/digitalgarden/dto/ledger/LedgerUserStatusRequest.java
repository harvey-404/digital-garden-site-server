package com.harvey.digitalgarden.dto.ledger;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LedgerUserStatusRequest {
    @NotBlank
    private String status;
}
