package com.harvey.digitalgarden.tracker;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.*;
import java.util.*;

public final class TrackerTypes {
  public enum Period {
    DAY,
    WEEK
  }

  public enum Metric {
    COUNT,
    DURATION
  }

  public enum Direction {
    MAX,
    MIN
  }

  public record Rule(
      Long id,
      Period period,
      Metric metric,
      Direction direction,
      long target,
      Direction sessionDirection,
      Long sessionSeconds,
      LocalDate effectiveFrom) {}

  public record Segment(Instant start, Instant end) {}

  public record Entry(
      long id,
      long activityId,
      int quantity,
      Instant startedAt,
      long durationSeconds,
      String note,
      boolean deleted,
      List<Segment> segments) {}

  public record Summary(
      LocalDate periodStart,
      LocalDate periodEnd,
      long count,
      long qualifiedCount,
      long durationSeconds,
      long sessionExceeded,
      long maxSessionSeconds,
      Long goalValue,
      Long remaining,
      long excess,
      String status,
      Rule rule) {}

  public record Activity(
      long id,
      String name,
      String unit,
      String template,
      boolean trackDuration,
      boolean archived,
      List<Rule> rules,
      Summary summary) {}

  public record RecordView(
      long id,
      long activityId,
      int quantity,
      Instant startedAt,
      long durationSeconds,
      String note,
      boolean deleted) {}

  public record Timer(
      long id,
      long activityId,
      Instant startedAt,
      Instant runningSince,
      long elapsedSeconds,
      String state) {}

  public record User(long id, String username, String timezone) {}

  public record Auth(String token, User user) {}

  public record RuleInput(
      Period period,
      Metric metric,
      Direction direction,
      @JsonDeserialize(using = TrackerNumbers.LongValue.class) Long target,
      Direction sessionDirection,
      @JsonDeserialize(using = TrackerNumbers.LongValue.class) Long sessionSeconds) {}

  public record ActivityInput(
      UUID requestId,
      String name,
      String unit,
      String template,
      boolean trackDuration,
      RuleInput rule) {}

  public record ActivityEdit(String name, String unit, RuleInput rule) {}

  public record RecordInput(
      UUID requestId,
      @JsonDeserialize(using = TrackerNumbers.IntValue.class) Integer quantity,
      Instant startedAt,
      @JsonDeserialize(using = TrackerNumbers.LongValue.class) Long durationSeconds,
      String note) {}

  public record Credentials(String username, String password, String timezone) {}

  public record NaturalInput(UUID requestId, String text) {}

  public record NaturalResult(String status, String message, Activity activity) {}
}
