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
}
