package com.harvey.digitalgarden.dto.ledger;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LedgerInviteRequest {
    @NotBlank(message = "邀请码不能为空")
    private String inviteCode;
}
