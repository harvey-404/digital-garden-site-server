package com.harvey.digitalgarden.tracker;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.harvey.digitalgarden.security.JwtUtil;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:tracker;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false"
    })
@Sql(
    scripts = "classpath:db/migration/V8__behavior_tracker.sql",
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class TrackerHttpTest {
  @LocalServerPort int port;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;
  @Autowired JwtUtil jwt;
  @MockBean TrackerGoalModel model;
  final HttpClient http = HttpClient.newHttpClient();

  JsonNode call(String method, String path, String token, Object body) {
    try {
      var b =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
              .header("Content-Type", "application/json");
      if (token != null) b.header("Authorization", "Bearer " + token);
      var response =
          http.send(
              b.method(
                      method,
                      body == null
                          ? HttpRequest.BodyPublishers.noBody()
                          : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      return json.readTree(response.body());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  JsonNode api(String method, String path, String token, Object body) {
    return call(method, "/api/tracker" + path, token, body);
  }

  JsonNode ok(String method, String path, String token, Object body) {
    var r = api(method, path, token, body);
    assertEquals(0, r.path("code").asInt(-1), r.toString());
    return r.path("data");
  }

  Map<String, Object> creds() {
    return Map.of(
        "username",
        "u_" + UUID.randomUUID().toString().substring(0, 10),
        "password",
        "password123",
        "timezone",
        "Asia/Shanghai");
  }

  String user() {
    return ok("POST", "/auth/register", null, creds()).path("token").asText();
  }

  Map<String, Object> rule() {
    return new HashMap<>(
        Map.of("period", "WEEK", "metric", "COUNT", "direction", "MAX", "target", 2));
  }

  Map<String, Object> activity(String name, boolean duration) {
    Map<String, Object> m =
        new HashMap<>(
            Map.of(
                "requestId",
                UUID.randomUUID().toString(),
                "name",
                name,
                "unit",
                "次",
                "template",
                "custom",
                "trackDuration",
                duration));
    m.put("rule", rule());
    return m;
  }

  Map<String, Object> entry() {
    return new HashMap<>(
        Map.of(
            "requestId",
            UUID.randomUUID().toString(),
            "quantity",
            1,
            "startedAt",
            Instant.now().minusSeconds(1800).toString(),
            "durationSeconds",
            600,
            "note",
            "测试"));
  }

  @Test
  void registrationLoginAndTokenIsolation() {
    var c = creds();
    var registered = ok("POST", "/auth/register", null, c);
    String token = registered.path("token").asText();
    assertEquals(registered.path("user"), ok("GET", "/auth/me", token, null));
    assertEquals(registered.path("user"), ok("POST", "/auth/login", null, c).path("user"));
    var wrong = new HashMap<>(c);
    wrong.put("password", "wrongpass123");
    assertEquals(401, api("POST", "/auth/login", null, wrong).path("code").asInt());
    assertEquals(409, api("POST", "/auth/register", null, c).path("code").asInt());
    assertEquals(401, api("GET", "/activities", null, null).path("code").asInt());
    assertEquals(
        403,
        api("GET", "/activities", jwt.generateAdminToken("test"), null).path("code").asInt(-1));
    assertNotEquals(
        0,
        api("GET", "/activities", jwt.generateLedgerToken(1, "test"), null).path("code").asInt(-1));
    assertNotEquals(0, call("GET", "/api/admin/posts", token, null).path("code").asInt(-1));
    assertNotEquals(0, call("GET", "/api/ledger/auth/me", token, null).path("code").asInt(-1));
    ok("PUT", "/auth/me", token, Map.of("timezone", "UTC"));
  }

  @Test
  void activityRecordHistoryIdempotencyAndReversibleDelete() {
    String token = user();
    var create = activity("跑步", true);
    var a = ok("POST", "/activities", token, create);
    long id = a.path("id").asLong();
    assertEquals(id, ok("POST", "/activities", token, create).path("id").asLong());
    assertEquals(
        409, api("POST", "/activities", token, activity(" 跑步 ", true)).path("code").asInt());
    var r = entry();
    var saved = ok("POST", "/activities/" + id + "/records", token, r);
    long rid = saved.path("id").asLong();
    assertEquals(rid, ok("POST", "/activities/" + id + "/records", token, r).path("id").asLong());
    r.put("quantity", 3);
    ok("PUT", "/records/" + rid, token, r);
    assertEquals(
        "OVER",
        ok("GET", "/activities", token, null).get(0).path("summary").path("status").asText());
    ok("PATCH", "/records/" + rid + "/deleted", token, Map.of("deleted", true));
    assertEquals(
        "EMPTY",
        ok("GET", "/activities", token, null).get(0).path("summary").path("status").asText());
    assertTrue(
        ok("GET", "/activities/" + id + "/records", token, null)
            .get(0)
            .path("deleted")
            .asBoolean());
    ok("PATCH", "/records/" + rid + "/deleted", token, Map.of("deleted", false));
    assertEquals(
        409, api("PUT", "/auth/me", token, Map.of("timezone", "UTC")).path("code").asInt());
    var edit = new HashMap<String, Object>(Map.of("name", "跑步", "unit", "次"));
    edit.put("rule", null);
    var edited = ok("PUT", "/activities/" + id, token, edit);
    assertEquals(2, edited.path("rules").size());
    assertEquals("OVER", edited.path("summary").path("status").asText());
    String next =
        LocalDate.now(ZoneId.of("Asia/Shanghai"))
            .with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY))
            .toString();
    assertEquals(next, edited.path("rules").get(1).path("effectiveFrom").asText());
    assertTrue(
        ok("GET", "/activities?date=" + next, token, null)
            .get(0)
            .path("summary")
            .path("rule")
            .isNull());
    ok("PATCH", "/activities/" + id + "/archive", token, Map.of("archived", true));
    assertEquals(0, ok("GET", "/activities", token, null).size());
    assertEquals(1, ok("GET", "/activities?archived=true", token, null).size());
    ok("PATCH", "/activities/" + id + "/archive", token, Map.of("archived", false));
  }

  @Test
  void everyResourceIdIsOwnerScoped() {
    String owner = user(), other = user();
    long a = ok("POST", "/activities", owner, activity("游泳", true)).path("id").asLong(),
        r = ok("POST", "/activities/" + a + "/records", owner, entry()).path("id").asLong(),
        t =
            ok("POST", "/activities/" + a + "/timer", owner, Map.of("requestId", UUID.randomUUID()))
                .path("id")
                .asLong();
    assertEquals(0, ok("GET", "/activities", other, null).size());
    assertEquals(
        404,
        api("PUT", "/activities/" + a, other, Map.of("name", "test", "unit", "次", "rule", rule()))
            .path("code")
            .asInt());
    assertEquals(
        404,
        api("PATCH", "/activities/" + a + "/archive", other, Map.of("archived", true))
            .path("code")
            .asInt());
    assertEquals(
        404, api("GET", "/activities/" + a + "/records", other, null).path("code").asInt());
    assertEquals(
        404, api("POST", "/activities/" + a + "/records", other, entry()).path("code").asInt());
    assertEquals(404, api("PUT", "/records/" + r, other, entry()).path("code").asInt());
    assertEquals(
        404,
        api("PATCH", "/records/" + r + "/deleted", other, Map.of("deleted", true))
            .path("code")
            .asInt());
    assertEquals(
        404,
        api("POST", "/activities/" + a + "/timer", other, Map.of("requestId", UUID.randomUUID()))
            .path("code")
            .asInt());
    for (String op : List.of("pause", "resume", "finish", "cancel"))
      assertEquals(404, api("POST", "/timer/" + t + "/" + op, other, null).path("code").asInt());
    assertTrue(ok("GET", "/timer", other, null).isNull());
  }

  @Test
  void persistedTimerSegmentsAndFinishRetries() {
    String token = user();
    var activity = activity("计时", true);
    activity.put("rule", null);
    long a = ok("POST", "/activities", token, activity).path("id").asLong();
    var req = Map.of("requestId", UUID.randomUUID());
    long t = ok("POST", "/activities/" + a + "/timer", token, req).path("id").asLong();
    assertEquals(t, ok("POST", "/activities/" + a + "/timer", token, req).path("id").asLong());
    assertEquals(
        409,
        api("POST", "/activities/" + a + "/timer", token, Map.of("requestId", UUID.randomUUID()))
            .path("code")
            .asInt());
    Instant started =
        LocalDate.now(ZoneId.of("Asia/Shanghai"))
            .minusDays(1)
            .atTime(23, 50)
            .atZone(ZoneId.of("Asia/Shanghai"))
            .toInstant();
    String segments =
        "[{\"start\":\""
            + started
            + "\",\"end\":\""
            + started.plusSeconds(600)
            + "\"},{\"start\":\""
            + started.plusSeconds(1800)
            + "\",\"end\":\""
            + started.plusSeconds(2400)
            + "\"}]";
    db.update(
        "UPDATE tracker_timer SET started_at=?,running_since=NULL,state='PAUSED',segments=? WHERE"
            + " id=?",
        started.toString(),
        segments,
        t);
    assertEquals(1200, ok("GET", "/timer", token, null).path("elapsedSeconds").asLong());
    ok("POST", "/timer/" + t + "/pause", token, null);
    ok("POST", "/timer/" + t + "/resume", token, null);
    ok("POST", "/timer/" + t + "/resume", token, null);
    ok("POST", "/timer/" + t + "/pause", token, null);
    var record = ok("POST", "/timer/" + t + "/finish", token, null);
    assertEquals(1200, record.path("durationSeconds").asLong());
    assertEquals(record, ok("POST", "/timer/" + t + "/finish", token, null));
    assertTrue(ok("GET", "/timer", token, null).isNull());
    assertEquals(
        600,
        ok(
                    "GET",
                    "/activities?period=DAY&date=" + LocalDate.now(ZoneId.of("Asia/Shanghai")),
                    token,
                    null)
                .get(0)
                .path("summary")
                .path("durationSeconds")
                .asLong()
            % 1200);
    long t2 =
        ok("POST", "/activities/" + a + "/timer", token, Map.of("requestId", UUID.randomUUID()))
            .path("id")
            .asLong();
    ok("POST", "/timer/" + t2 + "/cancel", token, null);
    ok("POST", "/timer/" + t2 + "/cancel", token, null);
  }

  @Test
  void naturalClarifiesValidatesSavesAndReplays() throws Exception {
    String token = user();
    when(model.parse(anyString()))
        .thenReturn(json.readTree("{\"status\":\"CLARIFY\",\"message\":\"每周几次？\"}"));
    assertEquals(
        "CLARIFY",
        ok("POST", "/goals/natural", token, Map.of("requestId", UUID.randomUUID(), "text", "我想跑步"))
            .path("status")
            .asText());
    assertEquals(0, ok("GET", "/activities", token, null).size());
    when(model.parse(anyString()))
        .thenReturn(
            json.readTree(
                "{\"status\":\"CREATE\",\"name\":\"跑步\",\"unit\":\"次\",\"template\":\"custom\",\"trackDuration\":true,\"rule\":{\"period\":\"WEEK\",\"metric\":\"COUNT\",\"direction\":\"MIN\",\"target\":3,\"sessionDirection\":\"MIN\",\"sessionSeconds\":1800},\"periodEvidence\":\"每周\",\"directionEvidence\":\"至少\",\"sessionDirectionEvidence\":\"至少\",\"targetEvidence\":\"3次\",\"sessionEvidence\":\"30分钟\"}"));
    var req = Map.of("requestId", UUID.randomUUID(), "text", "每周跑步至少3次，每次至少30分钟");
    var result = ok("POST", "/goals/natural", token, req);
    assertEquals("CREATED", result.path("status").asText());
    assertEquals(
        result.path("activity").path("id"),
        ok("POST", "/goals/natural", token, req).path("activity").path("id"));
    assertEquals(
        409,
        api(
                "POST",
                "/goals/natural",
                token,
                Map.of("requestId", UUID.randomUUID(), "text", req.get("text")))
            .path("code")
            .asInt());
    assertEquals(
        502,
        api("POST", "/goals/natural", token, Map.of("requestId", UUID.randomUUID(), "text", "想跑步"))
            .path("code")
            .asInt());
    when(model.parse(anyString()))
        .thenReturn(json.readTree("{\"status\":\"CREATE\",\"target\":-1}"));
    assertEquals(
        502,
        api(
                "POST",
                "/goals/natural",
                token,
                Map.of("requestId", UUID.randomUUID(), "text", "hello"))
            .path("code")
            .asInt());
    when(model.parse(anyString())).thenThrow(new TrackerException(503, "AI暂不可用，请使用手动创建"));
    assertEquals(
        503,
        api(
                "POST",
                "/goals/natural",
                token,
                Map.of("requestId", UUID.randomUUID(), "text", "hello"))
            .path("code")
            .asInt());
  }

  @Test
  void validationRejectsFutureAndInvalidBounds() {
    String token = user();
    long id = ok("POST", "/activities", token, activity("边界", true)).path("id").asLong();
    for (var invalid :
        List.of(
            Map.of("quantity", 0),
            Map.of("quantity", 1.5),
            Map.of("durationSeconds", 2.3),
            Map.of("quantity", 10001),
            Map.of("durationSeconds", 604801),
            Map.of("startedAt", Instant.now().plusSeconds(60).toString()))) {
      var r = entry();
      r.putAll(invalid);
      assertEquals(
          400, api("POST", "/activities/" + id + "/records", token, r).path("code").asInt());
    }
    var invalid = activity("invalid", false);
    var rule = rule();
    rule.put("sessionDirection", "MIN");
    invalid.put("rule", rule);
    assertEquals(400, api("POST", "/activities", token, invalid).path("code").asInt());
  }

  @Test
  void naturalHalfHourPersistsAndWrongDurationCannotWrite() throws Exception {
    String token = user();
    var goal = new TrackerGoalValidatorTest().halfHourGoal();
    ((com.fasterxml.jackson.databind.node.ObjectNode) goal.get("rule")).put("sessionSeconds", 3600);
    when(model.parse(anyString())).thenReturn(goal);
    String text = "每周至少学习三次，每次至少半小时";
    assertEquals(502, api("POST", "/goals/natural", token,
        Map.of("requestId", UUID.randomUUID(), "text", text)).path("code").asInt());
    assertEquals(0, ok("GET", "/activities", token, null).size());
    ((com.fasterxml.jackson.databind.node.ObjectNode) goal.get("rule")).put("sessionSeconds", 1800);
    var result = ok("POST", "/goals/natural", token,
        Map.of("requestId", UUID.randomUUID(), "text", text));
    assertEquals("CREATED", result.path("status").asText());
    var stored = ok("GET", "/activities", token, null);
    assertEquals(1, stored.size());
    assertEquals(1800, stored.get(0).path("rules").get(0).path("sessionSeconds").asLong());
    assertEquals(3, stored.get(0).path("rules").get(0).path("target").asLong());
  }

  @Test
  void concurrentStartsAndFinishRemainSingle() throws Exception {
    String token = user();
    long a = ok("POST", "/activities", token, activity("并发", true)).path("id").asLong();
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var request = Map.of("requestId", UUID.randomUUID());
      var one = pool.submit(() -> ok("POST", "/activities/" + a + "/timer", token, request));
      var two = pool.submit(() -> ok("POST", "/activities/" + a + "/timer", token, request));
      long id = one.get().path("id").asLong();
      assertEquals(id, two.get().path("id").asLong());
      var first = pool.submit(() -> ok("POST", "/timer/" + id + "/finish", token, null));
      var second = pool.submit(() -> ok("POST", "/timer/" + id + "/finish", token, null));
      assertEquals(first.get().path("id"), second.get().path("id"));
      assertEquals(1, ok("GET", "/activities/" + a + "/records", token, null).size());
    }
  }

  @Test
  void incorrectModelConstraintsClarifyWithoutWriting() throws Exception {
    String token = user();
    var helper = new TrackerGoalValidatorTest();
    var cases =
        List.of(
            Map.entry(
                "every week run at least 3 sessions, each at least 30 minutes",
                helper.english(30, "30 minutes", false)),
            Map.entry(
                "every week run at least 3 sessions, each at least 30 minutes",
                helper.english(3, "3 sessions", false)),
            Map.entry(
                "every week run at least 3 sessions and at most 2 sessions",
                helper.english(3, "3 sessions", false)),
            Map.entry(
                "every week run at least 3 sessions and every day at least 2 sessions",
                helper.english(3, "3 sessions", false)));
    for (var c : cases) {
      when(model.parse(anyString())).thenReturn(c.getValue());
      var result =
          ok(
              "POST",
              "/goals/natural",
              token,
              Map.of("requestId", UUID.randomUUID(), "text", c.getKey()));
      assertEquals("CLARIFY", result.path("status").asText());
      assertTrue(result.path("activity").isNull());
      assertEquals(0, ok("GET", "/activities", token, null).size());
    }
  }

  @Test
  void noteAndQuantityEditsPreservePausedMidnightSegments() throws Exception {
    String token = user();
    var request = activity("跨日暂停", true);
    request.put("rule", null);
    long activity = ok("POST", "/activities", token, request).path("id").asLong();
    long timer =
        ok(
                "POST",
                "/activities/" + activity + "/timer",
                token,
                Map.of("requestId", UUID.randomUUID()))
            .path("id")
            .asLong();
    LocalDate first = LocalDate.now(ZoneId.of("Asia/Shanghai")).minusDays(2);
    Instant start = first.atTime(23, 40).atZone(ZoneId.of("Asia/Shanghai")).toInstant();
    String segments =
        json.writeValueAsString(
            List.of(
                Map.of("start", start.toString(), "end", start.plusSeconds(600).toString()),
                Map.of(
                    "start",
                    start.plusSeconds(1800).toString(),
                    "end",
                    start.plusSeconds(2400).toString())));
    db.update(
        "UPDATE tracker_timer SET started_at=?,running_since=NULL,state='PAUSED',segments=? WHERE"
            + " id=?",
        start.toString(),
        segments,
        timer);
    var saved = ok("POST", "/timer/" + timer + "/finish", token, null);
    long id = saved.path("id").asLong();
    for (int quantity : List.of(1, 2)) {
      ok(
          "PUT",
          "/records/" + id,
          token,
          Map.of(
              "quantity",
              quantity,
              "startedAt",
              saved.path("startedAt").asText(),
              "durationSeconds",
              1200,
              "note",
              "改备注"));
      for (LocalDate date : List.of(first, first.plusDays(1)))
        assertEquals(
            600,
            ok("GET", "/activities?period=DAY&date=" + date, token, null)
                .get(0)
                .path("summary")
                .path("durationSeconds")
                .asLong());
    }
    ok(
        "PUT",
        "/records/" + id,
        token,
        Map.of(
            "quantity",
            2,
            "startedAt",
            start.plusSeconds(60).toString(),
            "durationSeconds",
            1200,
            "note",
            "主动改开始时间"));
    assertEquals(
        1140,
        ok("GET", "/activities?period=DAY&date=" + first, token, null)
            .get(0)
            .path("summary")
            .path("durationSeconds")
            .asLong());
  }

  @Test
  void dayToWeekWaitsForMondayAndDoesNotReclassifyEarlierRecords() {
    String token = user();
    var request = activity("周期切换", false);
    var daily = rule();
    daily.put("period", "DAY");
    daily.put("target", 10);
    request.put("rule", daily);
    long id = ok("POST", "/activities", token, request).path("id").asLong();
    var record = entry();
    record.put("quantity", 3);
    ok("POST", "/activities/" + id + "/records", token, record);
    var edited =
        ok("PUT", "/activities/" + id, token, Map.of("name", "周期切换", "unit", "次", "rule", rule()));
    LocalDate next =
        LocalDate.now(ZoneId.of("Asia/Shanghai"))
            .with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY));
    assertEquals(next.toString(), edited.path("rules").get(1).path("effectiveFrom").asText());
    var future = ok("GET", "/activities?date=" + next, token, null).get(0).path("summary");
    assertEquals("EMPTY", future.path("status").asText());
    assertEquals(0, future.path("count").asInt());
  }

  @Test
  void allPeriodTransitionsUseAlignedBoundaries() {
    String token = user();
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    for (String outgoing : List.of("DAY", "WEEK"))
      for (String incoming : List.of("DAY", "WEEK")) {
        String name = outgoing + incoming;
        var create = activity(name, false);
        var old = rule();
        old.put("period", outgoing);
        create.put("rule", old);
        long id = ok("POST", "/activities", token, create).path("id").asLong();
        var next = rule();
        next.put("period", incoming);
        var edited =
            ok("PUT", "/activities/" + id, token, Map.of("name", name, "unit", "次", "rule", next));
        LocalDate expected =
            outgoing.equals("WEEK") || incoming.equals("WEEK")
                ? today.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY))
                : today.plusDays(1);
        assertEquals(
            expected.toString(), edited.path("rules").get(1).path("effectiveFrom").asText());
      }
  }
}
