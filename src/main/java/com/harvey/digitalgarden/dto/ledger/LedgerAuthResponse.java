package com.harvey.digitalgarden.dto.ledger;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LedgerAuthResponse {
    private String token;
    private String userSn;
    private boolean onboardingDone;
}
