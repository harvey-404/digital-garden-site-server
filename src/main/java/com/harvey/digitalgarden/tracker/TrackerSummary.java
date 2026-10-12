package com.harvey.digitalgarden.tracker;

import static com.harvey.digitalgarden.tracker.TrackerTypes.*;

import com.harvey.digitalgarden.tracker.TrackerTypes.Period;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class TrackerSummary {
  public static LocalDate start(LocalDate date, Period period) {
    return period == Period.WEEK
        ? date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        : date;
  }

  public Summary calculate(
      LocalDate date, Period requested, ZoneId zone, List<Rule> rules, List<Entry> entries) {
    Rule rule =
        rules.stream()
            .filter(r -> !r.effectiveFrom().isAfter(date))
            .max(Comparator.comparing(Rule::effectiveFrom).thenComparing(Rule::id))
            .orElse(null);
    Period period = rule != null && rule.period() != null ? rule.period() : requested;
    LocalDate from = start(date, period), to = from.plusDays(period == Period.WEEK ? 7 : 1);
    Instant lo = from.atStartOfDay(zone).toInstant(), hi = to.atStartOfDay(zone).toInstant();
    long count = 0, qualified = 0, seconds = 0, exceeded = 0, max = 0;
    boolean any = false;
    for (Entry e : entries) {
      if (e.deleted()) continue;
      boolean starts = !e.startedAt().isBefore(lo) && e.startedAt().isBefore(hi);
      long overlap = 0;
      for (Segment s : e.segments()) {
        Instant a = s.start().isAfter(lo) ? s.start() : lo, b = s.end().isBefore(hi) ? s.end() : hi;
        if (b.isAfter(a)) overlap += Duration.between(a, b).getSeconds();
      }
      seconds += overlap;
      any |= starts || overlap > 0;
      if (starts) {
        count += e.quantity();
        max = Math.max(max, e.durationSeconds());
        boolean good =
            rule == null
                || rule.sessionDirection() == null
                || (rule.sessionDirection() == Direction.MIN
                    ? e.durationSeconds() >= rule.sessionSeconds()
                    : e.durationSeconds() <= rule.sessionSeconds());
        if (good) qualified += e.quantity();
        if (rule != null && rule.sessionDirection() == Direction.MAX && !good)
          exceeded += e.quantity();
      }
    }
    if (rule == null || rule.metric() == null)
      return new Summary(
          from,
          to,
          count,
          qualified,
          seconds,
          exceeded,
          max,
          null,
          null,
          0,
          any ? "TRACKING" : "EMPTY",
          null);
    long value = rule.metric() == Metric.DURATION ? seconds : qualified;
    long excess = rule.direction() == Direction.MAX ? Math.max(0, value - rule.target()) : 0,
        remaining = Math.max(0, rule.target() - value);
    String status =
        !any
            ? "EMPTY"
            : exceeded > 0 || excess > 0
                ? "OVER"
                : rule.direction() == Direction.MIN
                    ? (value >= rule.target() ? "DONE" : "TRACKING")
                    : (value == rule.target() ? "AT_LIMIT" : "WITHIN");
    // A maximum periodic count counts every session, even sessions that violate their own
    // condition.
    if (rule.metric() == Metric.COUNT && rule.direction() == Direction.MAX) {
      value = count;
      excess = Math.max(0, value - rule.target());
      remaining = Math.max(0, rule.target() - value);
      status =
          !any
              ? "EMPTY"
              : exceeded > 0 || excess > 0
                  ? "OVER"
                  : value == rule.target() ? "AT_LIMIT" : "WITHIN";
    }
    return new Summary(
        from,
        to,
        count,
        qualified,
        seconds,
        exceeded,
        max,
        rule.target(),
        remaining,
        excess,
        status,
        rule);
  }
}
