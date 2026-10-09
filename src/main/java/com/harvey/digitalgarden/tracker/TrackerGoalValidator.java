package com.harvey.digitalgarden.tracker;

import static com.harvey.digitalgarden.tracker.TrackerTypes.*;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Component;

@Component
public class TrackerGoalValidator {
  public ActivityInput validate(UUID id, String text, JsonNode n) {
    var grounding = new TrackerGoalGrounding();
    text = grounding.effectiveText(text);
    try {
      if (n == null
          || !n.isObject()
          || !n.path("status").asText().equals("CREATE")
          || !n.path("trackDuration").isBoolean()) throw new IllegalArgumentException();
      String name = string(n, "name", 80),
          unit = string(n, "unit", 20),
          template = string(n, "template", 40);
      RuleInput rule = null;
      JsonNode r = n.get("rule");
      if (r == null) throw new IllegalArgumentException();
      if (!r.isNull()) {
        if (!r.isObject()
            || !r.path("target").isIntegralNumber()
            || !r.path("target").canConvertToLong()) throw new IllegalArgumentException();
        var period = Period.valueOf(r.path("period").asText());
        var metric = Metric.valueOf(r.path("metric").asText());
        var direction = Direction.valueOf(r.path("direction").asText());
        JsonNode sd = r.get("sessionDirection"), ss = r.get("sessionSeconds");
        if (sd == null || ss == null) throw new IllegalArgumentException();
        Direction session = sd.isNull() ? null : Direction.valueOf(sd.asText());
        Long seconds =
            ss.isNull()
                ? null
                : ss.isIntegralNumber() && ss.canConvertToLong() ? ss.longValue() : -1;
        rule =
            new RuleInput(
                period, metric, direction, r.path("target").longValue(), session, seconds);
        TrackerValidation.rule(rule);
        semanticEvidence(
            text,
            n.get("periodEvidence"),
            period == Period.DAY
                ? "(?i).*(每天|每日|一天|daily|per day|each day|every day).*"
                : "(?i).*(每周|每星期|一周|weekly|per week|each week|every week).*");
        semanticEvidence(
            text,
            n.get("directionEvidence"),
            direction == Direction.MAX
                ? "(?i).*(最多|不超过|至多|上限|at most|no more than|max).*"
                : "(?i).*(至少|不少于|最低|at least|min).*");
        if (session != null)
          semanticEvidence(
              text,
              n.get("sessionDirectionEvidence"),
              session == Direction.MAX
                  ? "(?i).*(最多|不超过|至多|上限|at most|no more than|max).*"
                  : "(?i).*(至少|不少于|最低|at least|min).*");
        evidence(text, n.get("targetEvidence"), rule.target(), metric == Metric.DURATION);
        if (seconds != null) evidence(text, n.get("sessionEvidence"), seconds, true);
      }
      if (r.isNull())
        semanticEvidence(
            text,
            n.get("statisticsEvidence"),
            "(?i).*(仅统计|只记录|只统计|不设目标|无需目标|没有目标|without a goal|no goal|statistics only).*");
      grounding.verify(text, unit, rule, this);
      return new ActivityInput(
          id, name, unit, template, n.path("trackDuration").booleanValue(), rule);
    } catch (TrackerGoalAmbiguity e) {
      throw e;
    } catch (Exception e) {
      throw new TrackerException(502, "AI返回的目标不完整或格式无效，请补充说明或手动创建");
    }
  }

  void semanticEvidence(String text, JsonNode node, String pattern) {
    if (node == null
        || !node.isTextual()
        || node.textValue().isBlank()
        || !text.contains(node.textValue())
        || !node.textValue().matches(pattern)) throw new IllegalArgumentException();
  }

  String string(JsonNode n, String key, int max) {
    if (!n.path(key).isTextual()) throw new IllegalArgumentException();
    return TrackerValidation.text(n.path(key).textValue(), max, key);
  }

  // This only validates the provider's quoted numeric evidence; goal extraction always uses the
  // provider.
  void evidence(String text, JsonNode node, long expected, boolean duration) {
    if (node == null
        || !node.isTextual()
        || node.textValue().isBlank()
        || !text.contains(node.textValue())) throw new IllegalArgumentException();
    String evidence = node.textValue();
    Matcher m = Pattern.compile("(?:[0-9]+(?:\\.[0-9]+)?|[零〇一二两三四五六七八九十百千万]+)").matcher(evidence);
    boolean matches = false;
    while (m.find()) {
      double value =
          m.group().matches("[0-9].*") ? Double.parseDouble(m.group()) : chinese(m.group());
      String tail = evidence.substring(m.end()).stripLeading();
      if (duration) {
        if (tail.startsWith("小时") || tail.startsWith("hour")) value *= 3600;
        else if (tail.startsWith("分钟")
            || tail.startsWith("分")
            || tail.startsWith("minute")
            || tail.startsWith("min")) value *= 60;
        else if (!tail.startsWith("秒") && !tail.startsWith("sec")) continue;
      }
      if (value == expected) matches = true;
    }
    if (!matches) throw new IllegalArgumentException();
  }

  long chinese(String s) {
    long total = 0, current = 0;
    String digits = "零一二三四五六七八九";
    for (char c : s.toCharArray()) {
      if (c == '两') current = 2;
      else if (c == '〇') current = 0;
      else if (digits.indexOf(c) >= 0) current = digits.indexOf(c);
      else {
        long unit = c == '十' ? 10 : c == '百' ? 100 : c == '千' ? 1000 : 10000;
        total += (current == 0 ? 1 : current) * unit;
        current = 0;
      }
    }
    return total + current;
  }
}
