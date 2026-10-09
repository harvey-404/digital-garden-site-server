package com.harvey.digitalgarden.tracker;

import static com.harvey.digitalgarden.tracker.TrackerTypes.*;
import static org.junit.jupiter.api.Assertions.*;

import com.harvey.digitalgarden.tracker.TrackerTypes.Period;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class TrackerSummaryTest {
  final TrackerSummary calculator = new TrackerSummary();
  final ZoneId zone = ZoneId.of("Asia/Shanghai");
  final LocalDate day = LocalDate.of(2026, 10, 8);

  Rule rule(Metric m, Direction d, long target, Direction sd, Long secs) {
    return new Rule(1L, Period.DAY, m, d, target, sd, secs, day.minusDays(20));
  }

  Entry entry(int q, String start, long sec) {
    Instant s = Instant.parse(start);
    return new Entry(1, 1, q, s, sec, "", false, List.of(new Segment(s, s.plusSeconds(sec))));
  }

  Summary sum(Rule r, Entry... e) {
    return calculator.calculate(
        day, Period.DAY, zone, r == null ? List.of() : List.of(r), List.of(e));
  }

  @Test
  void countEqualityAndOverage() {
    var r = rule(Metric.COUNT, Direction.MAX, 2, null, null);
    assertEquals("AT_LIMIT", sum(r, entry(2, "2026-10-08T02:00:00Z", 0)).status());
    var s = sum(r, entry(3, "2026-10-08T02:00:00Z", 0));
    assertEquals("OVER", s.status());
    assertEquals(1, s.excess());
  }

  @Test
  void totalDurationEquality() {
    assertEquals(
        "AT_LIMIT",
        sum(
                rule(Metric.DURATION, Direction.MAX, 3600, null, null),
                entry(1, "2026-10-08T02:00:00Z", 3600))
            .status());
  }

  @Test
  void sessionsAreIndependent() {
    var s =
        sum(
            rule(Metric.COUNT, Direction.MAX, 2, Direction.MAX, 3600L),
            entry(1, "2026-10-08T02:00:00Z", 4200),
            entry(1, "2026-10-08T04:00:00Z", 1500));
    assertEquals(2, s.count());
    assertEquals(1, s.sessionExceeded());
    assertEquals("OVER", s.status());
  }

  @Test
  void minimumCountsOnlyQualified() {
    var s =
        sum(
            rule(Metric.COUNT, Direction.MIN, 3, Direction.MIN, 1800L),
            entry(1, "2026-10-08T02:00:00Z", 900),
            entry(1, "2026-10-08T04:00:00Z", 1800));
    assertEquals(2, s.count());
    assertEquals(1, s.qualifiedCount());
    assertEquals(2, s.remaining());
  }

  @Test
  void statisticsAndEmpty() {
    assertEquals("TRACKING", sum(null, entry(1, "2026-10-08T02:00:00Z", 0)).status());
    assertEquals("EMPTY", sum(rule(Metric.COUNT, Direction.MAX, 2, null, null)).status());
  }

  @Test
  void midnightSplitsDurationAndCountAtStart() {
    var e = entry(1, "2026-10-08T15:50:00Z", 1800);
    assertEquals(600, sum(null, e).durationSeconds());
    var s = calculator.calculate(day.plusDays(1), Period.DAY, zone, List.of(), List.of(e));
    assertEquals(1200, s.durationSeconds());
    assertEquals(0, s.count());
  }

  @Test
  void mondayBoundary() {
    var s =
        calculator.calculate(
            day, Period.WEEK, zone, List.of(), List.of(entry(1, "2026-10-04T15:59:00Z", 120)));
    assertEquals(LocalDate.of(2026, 10, 5), s.periodStart());
    assertEquals(0, s.count());
    assertEquals(60, s.durationSeconds());
  }

  @Test
  void historicalVersionSelected() {
    var old = rule(Metric.COUNT, Direction.MAX, 2, null, null);
    var next =
        new Rule(2L, Period.DAY, Metric.COUNT, Direction.MAX, 5, null, null, day.plusDays(1));
    assertEquals(
        1L, calculator.calculate(day, Period.DAY, zone, List.of(old, next), List.of()).rule().id());
  }

  @Test
  void pausedGapExcluded() {
    var s = Instant.parse("2026-10-08T15:50:00Z");
    var e =
        new Entry(
            1,
            1,
            1,
            s,
            1200,
            "",
            false,
            List.of(
                new Segment(s, s.plusSeconds(600)),
                new Segment(s.plusSeconds(1800), s.plusSeconds(2400))));
    assertEquals(600, sum(null, e).durationSeconds());
    assertEquals(
        600,
        calculator
            .calculate(day.plusDays(1), Period.DAY, zone, List.of(), List.of(e))
            .durationSeconds());
  }
}
