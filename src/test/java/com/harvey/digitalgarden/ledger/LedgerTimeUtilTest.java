package com.harvey.digitalgarden.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class LedgerTimeUtilTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    void monthStart_sept1_0030_shanghai_is_september() {
        long ts =
                ZonedDateTime.of(2026, 9, 1, 0, 30, 0, 0, SHANGHAI).toEpochSecond();
        long start = LedgerTimeUtil.monthStartShanghai(ts);
        long expected =
                ZonedDateTime.of(2026, 9, 1, 0, 0, 0, 0, SHANGHAI).toEpochSecond();
        assertEquals(expected, start);
    }

    @Test
    void parseMonth_2026_09() {
        long septTs =
                ZonedDateTime.of(2026, 9, 15, 12, 0, 0, 0, SHANGHAI).toEpochSecond();
        assertEquals(LedgerTimeUtil.monthStartShanghai(septTs), LedgerTimeUtil.parseMonth("2026-09"));
    }
}
