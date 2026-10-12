package com.harvey.digitalgarden.tracker;

import static com.harvey.digitalgarden.tracker.TrackerTypes.*;

import java.util.*;
import java.util.regex.*;

/**
 * A conservative verifier of the model's proposed constraints, not a goal extractor. It can only
 * accept or request clarification; it cannot create/replace model fields.
 */
final class TrackerGoalGrounding {
  private static final String NUMBER = TrackerGoalValidator.NUMBER;
  private static final String MAX = "最多|不超过|至多|上限|at most|no more than|maximum|max\\b";
  private static final String MIN = "至少|不少于|最低|at least|minimum|min\\b";
  private static final String DURATION =
      "分钟|小时|秒钟|秒|分|minutes?\\b|mins?\\b|hours?\\b|hrs?\\b|seconds?\\b|secs?\\b";
  private static final Pattern DAY =
      Pattern.compile(
          "每天|每日|一天|\\bdaily\\b|\\b(?:per|each|every) day\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern WEEK =
      Pattern.compile(
          "每周|每星期|一周|\\bweekly\\b|\\b(?:per|each|every) week\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern SESSION =
      Pattern.compile(
          "每次|单次|每场|每一场|\\bper session\\b|\\beach session\\b|\\beach(?!\\s+(?:day|week)\\b)",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern CORRECTION =
      Pattern.compile(
          "改为|更正为|改成|更正[:：]?|\\bcorrection\\s*:|\\bi mean\\b|\\bchange (?:that )?to\\b",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern RECORDING =
      Pattern.compile(
          "记一笔|记录一下|记一下|刚刚|刚才|已经|(?:今天|昨天).{0,20}(?:喝了|跑了|做了|看了)|\\bI (?:ran|drank|did|have"
              + " done|already)\\b|\\brecord (?:that|my)\\b",
          Pattern.CASE_INSENSITIVE);

  private record Constraint(
      Direction direction,
      Metric metric,
      double value,
      String unit,
      boolean session,
      int start,
      int end) {}

  String effectiveText(String text) {
    Matcher correction = CORRECTION.matcher(text);
    int end = -1;
    while (correction.find()) end = correction.end();
    return end < 0 ? text : text.substring(end).strip();
  }

  void verify(String text, String unit, RuleInput rule, TrackerGoalValidator numbers) {
    if (RECORDING.matcher(text).find()) throw clarify("这是一次实际记录吗？请使用记录入口，或明确说明要创建的目标。");
    boolean day = DAY.matcher(text).find(), week = WEEK.matcher(text).find();
    if (day && week) throw clarify("描述中有每天和每周两种周期，请明确一个周期后重试。");
    if (rule == null) {
      if (Pattern.compile(MAX + "|" + MIN, Pattern.CASE_INSENSITIVE).matcher(text).find()
          || SESSION.matcher(text).find()) throw clarify("仅统计和目标条件同时出现，请明确是否保留目标。");
      return;
    }
    if (!day && !week || rule.period() != (week ? Period.WEEK : Period.DAY))
      throw clarify("请明确目标按每天还是每周统计。");
    String countUnits =
        "次|杯|瓶|支|份|本|页|个|组|场|遍|颗|粒|碗|块|件|sessions?\\b|times?\\b|cups?\\b|bottles?\\b|pages?\\b|sets?\\b|"
            + Pattern.quote(unit);
    String units = "(?:" + DURATION + "|" + countUnits + ")";
    Pattern condition =
        Pattern.compile(
            "("
                + MAX
                + "|"
                + MIN
                + ")([^0-9零〇一二两三四五六七八九十百千万半,，;；.!。!?？]{0,40}?)\\s*("
                + NUMBER
                + ")\\s*("
                + units
                + ")",
            Pattern.CASE_INSENSITIVE);
    List<Constraint> main = new ArrayList<>(), session = new ArrayList<>(), all = new ArrayList<>();
    Matcher match = condition.matcher(text);
    while (match.find()) {
      String quantity = match.group(3), foundUnit = match.group(4);
      double value = numbers.number(quantity);
      boolean duration =
          Pattern.compile("^(?:" + DURATION + ")$", Pattern.CASE_INSENSITIVE)
              .matcher(foundUnit)
              .matches();
      if (duration) {
        String lower = foundUnit.toLowerCase(Locale.ROOT);
        if (lower.equals("小时") || lower.startsWith("hour") || lower.startsWith("hr")) value *= 3600;
        else if (lower.equals("分钟") || lower.equals("分") || lower.startsWith("min")) value *= 60;
      }
      boolean isSession =
          last(SESSION, text, match.start())
              > Math.max(last(DAY, text, match.start()), last(WEEK, text, match.start()));
      String suffix = text.substring(match.end()).split("[,，;；.!。!?？]", 2)[0];
      if (suffix.stripLeading().matches("(?i)^(?:per session|each session).*")) isSession = true;
      Direction direction =
          Pattern.compile("^(?:" + MAX + ")$", Pattern.CASE_INSENSITIVE)
                  .matcher(match.group(1))
                  .matches()
              ? Direction.MAX
              : Direction.MIN;
      Constraint c =
          new Constraint(
              direction,
              duration ? Metric.DURATION : Metric.COUNT,
              value,
              foundUnit,
              isSession,
              match.start(),
              match.end());
      all.add(c);
      (isSession ? session : main).add(c);
    }
    // Every measured threshold in the supported vocabulary needs its own explicit direction.
    Matcher quantity =
        Pattern.compile(NUMBER + "\\s*" + units, Pattern.CASE_INSENSITIVE).matcher(text);
    while (quantity.find()) {
      int start = quantity.start(), end = quantity.end();
      if (all.stream().noneMatch(c -> start >= c.start() && end <= c.end()))
        throw clarify("有未明确上下限的数量或时长，请为每个条件注明至少或最多。");
    }
    if (main.size() != 1) throw clarify("请保留一个周期目标；多个上下限或不同目标需要进一步确认。");
    Constraint periodic = main.getFirst();
    if (periodic.metric() == Metric.COUNT
        && !canonicalUnit(periodic.unit()).equals(canonicalUnit(unit)))
      throw clarify("次数单位与原文不一致，请确认按次、杯还是其他单位记录。");
    if (periodic.metric() != rule.metric()
        || periodic.direction() != rule.direction()
        || periodic.value() != rule.target()) throw clarify("识别的目标与原文数量、单位或方向不一致，请重新说明周期目标。");
    boolean hasSession = SESSION.matcher(text).find();
    if (hasSession && (session.size() != 1 || rule.sessionDirection() == null))
      throw clarify("请明确每次的时长上下限，单次条件不能省略。");
    if (!hasSession && (rule.sessionDirection() != null || !session.isEmpty()))
      throw clarify("原文没有明确单次条件，请确认是否需要单次时长目标。");
    if (!session.isEmpty()) {
      Constraint s = session.getFirst();
      if (s.metric() != Metric.DURATION
          || s.direction() != rule.sessionDirection()
          || s.value() != rule.sessionSeconds()) throw clarify("单次时长与周期目标不同，请明确每次至少或最多多少分钟。");
    }
  }

  private int last(Pattern pattern, String text, int until) {
    Matcher matcher = pattern.matcher(text.substring(0, until));
    int last = -1;
    while (matcher.find()) last = matcher.start();
    return last;
  }

  private String canonicalUnit(String unit) {
    return switch (unit.toLowerCase(Locale.ROOT)) {
      case "次", "session", "sessions", "time", "times" -> "次";
      case "杯", "cup", "cups" -> "杯";
      case "瓶", "bottle", "bottles" -> "瓶";
      case "页", "page", "pages" -> "页";
      case "组", "set", "sets" -> "组";
      default -> unit.toLowerCase(Locale.ROOT);
    };
  }

  private TrackerGoalAmbiguity clarify(String message) {
    return new TrackerGoalAmbiguity(message);
  }
}
