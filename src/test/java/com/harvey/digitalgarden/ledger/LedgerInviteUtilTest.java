package com.harvey.digitalgarden.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LedgerInviteUtilTest {

    @Test
    void normalize_strips_dashes_and_uppercases() {
        assertEquals("ABCD-EFGH-JKLM", LedgerInviteUtil.normalizeInviteCode("abcd-efgh-jklm"));
    }
}
