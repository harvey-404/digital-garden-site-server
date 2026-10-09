package com.harvey.digitalgarden.tracker;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class TrackerGoalValidatorTest {
  final ObjectMapper json = new ObjectMapper();
  final TrackerGoalValidator validator = new TrackerGoalValidator();

  JsonNode goal() throws Exception {
    return json.readTree(
        "{\"status\":\"CREATE\",\"name\":\"跑步\",\"unit\":\"次\",\"template\":\"custom\",\"trackDuration\":true,\"rule\":{\"period\":\"WEEK\",\"metric\":\"COUNT\",\"direction\":\"MIN\",\"target\":3,\"sessionDirection\":\"MIN\",\"sessionSeconds\":1800},\"targetEvidence\":\"3次\",\"sessionEvidence\":\"30分钟\"}");
  }

  @Test
  void missingPeriodAndDirectionCannotBeInvented() throws Exception {
    assertThrows(
        TrackerException.class, () -> validator.validate(UUID.randomUUID(), "跑步3次，每次30分钟", goal()));
  }

  @Test
  void noGoalNeedsExplicitEvidence() throws Exception {
    var n = (com.fasterxml.jackson.databind.node.ObjectNode) goal();
    n.putNull("rule");
    assertThrows(TrackerException.class, () -> validator.validate(UUID.randomUUID(), "跑步", n));
  }

  @Test
  void numericEvidenceCannotInventThreshold() throws Exception {
    assertThrows(
        TrackerException.class,
        () -> validator.validate(UUID.randomUUID(), "每周跑步至少2次，每次至少20分钟", goal()));
  }

  JsonNode english(long target, String evidence, boolean session) {
    var n = json.createObjectNode();
    n.put("status", "CREATE");
    n.put("name", "run");
    n.put("unit", "sessions");
    n.put("template", "custom");
    n.put("trackDuration", true);
    var r = n.putObject("rule");
    r.put("period", "WEEK");
    r.put("metric", "COUNT");
    r.put("direction", "MIN");
    r.put("target", target);
    if (session) {
      r.put("sessionDirection", "MIN");
      r.put("sessionSeconds", 1800);
      n.put("sessionEvidence", "30 minutes");
      n.put("sessionDirectionEvidence", "at least");
    } else {
      r.putNull("sessionDirection");
      r.putNull("sessionSeconds");
    }
    n.put("targetEvidence", evidence);
    n.put("periodEvidence", "every week");
    n.put("directionEvidence", "at least");
    return n;
  }

  @Test
  void durationPhraseCannotBecomeCountThreshold() {
    assertThrows(
        TrackerException.class,
        () ->
            validator.validate(
                UUID.randomUUID(),
                "every week run at least 3 sessions, each at least 30 minutes",
                english(30, "30 minutes", false)));
  }

  @Test
  void explicitSessionConditionCannotBeDropped() {
    assertThrows(
        TrackerException.class,
        () ->
            validator.validate(
                UUID.randomUUID(),
                "every week run at least 3 sessions, each at least 30 minutes",
                english(3, "3 sessions", false)));
  }

  @Test
  void contradictoryPeriodicDirectionsCannotBeDropped() {
    assertThrows(
        TrackerException.class,
        () ->
            validator.validate(
                UUID.randomUUID(),
                "every week run at least 3 sessions and at most 2 sessions",
                english(3, "3 sessions", false)));
  }

  @Test
  void conflictingPeriodsCannotBeDropped() {
    assertThrows(
        TrackerException.class,
        () ->
            validator.validate(
                UUID.randomUUID(),
                "every week run at least 3 sessions and every day at least 2 sessions",
                english(3, "3 sessions", false)));
  }

  @Test
  void completeCountAndMixedSessionDirectionsRemainValid() {
    var n = (com.fasterxml.jackson.databind.node.ObjectNode) english(3, "3 sessions", true);
    ((com.fasterxml.jackson.databind.node.ObjectNode) n.get("rule")).put("sessionDirection", "MAX");
    n.put("sessionDirectionEvidence", "at most");
    assertDoesNotThrow(
        () ->
            validator.validate(
                UUID.randomUUID(),
                "every week run at least 3 sessions, each at most 30 minutes",
                n));
  }

  @Test
  void completeDurationAndExplicitCorrectionRemainValid() {
    var n = (com.fasterxml.jackson.databind.node.ObjectNode) english(1800, "30 minutes", false);
    ((com.fasterxml.jackson.databind.node.ObjectNode) n.get("rule")).put("metric", "DURATION");
    assertDoesNotThrow(
        () -> validator.validate(UUID.randomUUID(), "every week run at least 30 minutes", n));
    assertDoesNotThrow(
        () ->
            validator.validate(
                UUID.randomUUID(),
                "every week run at most 2 sessions; correction: every week run at least 3 sessions",
                english(3, "3 sessions", false)));
  }

  @Test
  void countUnitMustMatchSource() {
    assertThrows(
        TrackerException.class,
        () ->
            validator.validate(
                UUID.randomUUID(),
                "every week drink at least 3 cups",
                english(3, "3 cups", false)));
  }

  @Test
  void chineseMixedDirectionGoalRemainsValid() {
    var n = (com.fasterxml.jackson.databind.node.ObjectNode) english(3, "3次", true);
    n.put("unit", "次");
    n.put("periodEvidence", "每周");
    n.put("directionEvidence", "至少");
    n.put("sessionEvidence", "30分钟");
    n.put("sessionDirectionEvidence", "最多");
    ((com.fasterxml.jackson.databind.node.ObjectNode) n.get("rule")).put("sessionDirection", "MAX");
    assertDoesNotThrow(() -> validator.validate(UUID.randomUUID(), "每周跑步至少3次，每次最多30分钟", n));
  }

  @Test
  void recordingIntentCannotBecomeGoal() {
    assertThrows(
        TrackerException.class,
        () ->
            validator.validate(
                UUID.randomUUID(),
                "I ran today; record that. My goal was every week run at least 3 sessions",
                english(3, "3 sessions", false)));
  }
}
