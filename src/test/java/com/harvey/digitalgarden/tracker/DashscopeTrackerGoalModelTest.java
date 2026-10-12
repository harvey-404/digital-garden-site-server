package com.harvey.digitalgarden.tracker;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.*;
import org.junit.jupiter.api.*;

class DashscopeTrackerGoalModelTest {
  static final String TEXT = "每周至少学习三次，每次至少半小时";
  final ObjectMapper json = new ObjectMapper();
  final List<JsonNode> requests = new ArrayList<>();
  final Queue<JsonNode> responses = new ArrayDeque<>();
  final TrackerGoalValidator validator = new TrackerGoalValidator();
  HttpServer server;
  DashscopeTrackerGoalModel model;

  @BeforeEach
  void startProvider() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/chat/completions", exchange -> {
      requests.add(json.readTree(exchange.getRequestBody()));
      JsonNode next = responses.poll();
      byte[] body = json.writeValueAsBytes(Map.of("choices", List.of(Map.of(
          "finish_reason", "stop", "message", Map.of("content", next == null ? "{}" : next.toString())))));
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(next == null ? 503 : 200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    model = new DashscopeTrackerGoalModel(json, "local-fixture-key",
        "http://127.0.0.1:" + server.getAddress().getPort(), "fixture-model");
  }

  @AfterEach
  void stopProvider() { server.stop(0); }

  ObjectNode capturedFailure() throws Exception {
    return (ObjectNode) json.readTree("""
        {"status":"CREATE","name":"学习","unit":"次","template":"custom","trackDuration":true,
         "rule":{"period":"WEEK","metric":"COUNT","direction":"MIN","target":3,"sessionDirection":"MIN","sessionSeconds":1800},
         "targetEvidence":"3次","sessionEvidence":"半小时","periodEvidence":"每周","directionEvidence":"至少","sessionDirectionEvidence":"至少"}
        """);
  }

  @Test
  void normalizedChineseEvidenceIsRepairedByProviderThenFullyValidated() throws Exception {
    var wrong = capturedFailure();
    assertThrows(TrackerException.class, () -> validator.validate(UUID.randomUUID(), TEXT, wrong));
    responses.add(wrong);
    responses.add(capturedFailure().put("targetEvidence", "三次"));
    var result = model.parse(TEXT);
    var activity = validator.validate(UUID.randomUUID(), TEXT, result);
    assertEquals(3, activity.rule().target());
    assertEquals(1800, activity.rule().sessionSeconds());
    assertEquals("三次", result.path("targetEvidence").asText());
    assertEquals(2, requests.size());
    var repairMessages = requests.get(1).path("messages");
    assertEquals("system", repairMessages.get(0).path("role").asText());
    assertTrue(repairMessages.get(0).path("content").asText().contains("verbatim"));
    assertTrue(repairMessages.get(0).path("content").asText().contains("untrusted"));
    assertTrue(repairMessages.toString().contains("3次"), "model receives the failed output as data");
    assertEquals("user", repairMessages.get(repairMessages.size() - 1).path("role").asText());
    assertEquals(TEXT, repairMessages.get(repairMessages.size() - 1).path("content").asText());
  }

  @Test
  void secondNonverbatimResponseStopsAndRemainsInvalid() throws Exception {
    responses.add(capturedFailure());
    responses.add(capturedFailure());
    var result = model.parse(TEXT);
    assertEquals(2, requests.size());
    assertEquals("3次", result.path("targetEvidence").asText());
    assertThrows(TrackerException.class, () -> validator.validate(UUID.randomUUID(), TEXT, result));
  }

  @Test
  void repairCannotBypassNumericGrounding() throws Exception {
    responses.add(capturedFailure());
    var repair = capturedFailure().put("targetEvidence", "三次");
    ((ObjectNode) repair.get("rule")).put("target", 4);
    responses.add(repair);
    var result = model.parse(TEXT);
    assertEquals(2, requests.size());
    assertThrows(TrackerException.class, () -> validator.validate(UUID.randomUUID(), TEXT, result));
  }

  @Test
  void validVerbatimEvidenceNeedsOnlyOneProviderCall() throws Exception {
    responses.add(capturedFailure().put("targetEvidence", "三次"));
    assertDoesNotThrow(() -> validator.validate(UUID.randomUUID(), TEXT, model.parse(TEXT)));
    assertEquals(1, requests.size());
  }

  @Test
  void clarificationIsReturnedWithoutRetry() {
    responses.add(json.createObjectNode().put("status", "CLARIFY").put("message", "每周几次？"));
    assertEquals("CLARIFY", model.parse("我想学习").path("status").asText());
    assertEquals(1, requests.size());
  }

  @Test
  void providerFailureIsNotRetriedAndReleasesConcurrencySlot() {
    assertEquals(503, assertThrows(TrackerException.class, () -> model.parse(TEXT)).code);
    assertEquals(1, requests.size());
    assertEquals(2, model.slots.availablePermits());
  }
}
