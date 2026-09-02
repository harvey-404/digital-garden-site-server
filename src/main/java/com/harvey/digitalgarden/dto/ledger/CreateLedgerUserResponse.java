package com.harvey.digitalgarden.dto.ledger;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateLedgerUserResponse {
    private String userSn;
    private String inviteCode;
}
