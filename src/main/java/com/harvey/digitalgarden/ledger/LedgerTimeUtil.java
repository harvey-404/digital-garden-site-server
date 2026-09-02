package com.harvey.digitalgarden.ledger;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

public final class LedgerTimeUtil {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private LedgerTimeUtil() {}

    public static long monthStartShanghai(long epochSec) {
        ZonedDateTime zdt = Instant.ofEpochSecond(epochSec).atZone(SHANGHAI);
        return zdt.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS).toEpochSecond();
    }

    public static long monthEndShanghai(long epochSec) {
        ZonedDateTime zdt = Instant.ofEpochSecond(epochSec).atZone(SHANGHAI);
        ZonedDateTime monthStart = zdt.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
        return monthStart.plusMonths(1).toEpochSecond();
    }

    public static long parseMonth(String yyyyMm) {
        YearMonth yearMonth = YearMonth.parse(yyyyMm);
        return yearMonth.atDay(1).atStartOfDay(SHANGHAI).toEpochSecond();
    }

    public static long[] monthRange(String yyyyMm) {
        long start = parseMonth(yyyyMm);
        YearMonth yearMonth = YearMonth.parse(yyyyMm);
        long end = yearMonth.plusMonths(1).atDay(1).atStartOfDay(SHANGHAI).toEpochSecond();
        return new long[] {start, end};
    }
}
