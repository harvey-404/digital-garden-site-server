package com.harvey.digitalgarden.account;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.harvey.digitalgarden.security.JwtUtil;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
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
      "spring.datasource.url=jdbc:h2:mem:account;MODE=MySQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.datasource.hikari.transaction-isolation=TRANSACTION_REPEATABLE_READ",
      "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.flyway.enabled=false",
      "app.account.origin=https://www.harvey404.com",
      "app.cors.allowed-origins=https://www.harvey404.com",
      "app.account.secure-cookie=true",
      "app.account.service-key=test-site-service-key-with-at-least-32-bytes"
    })
@Sql(
    scripts = {
      "classpath:db/migration/V8__behavior_tracker.sql",
      "classpath:db/migration/V9__unified_account.sql"
    },
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class AccountHttpTest {
  @LocalServerPort int port;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired JwtUtil jwt;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

  @org.springframework.boot.test.mock.mockito.SpyBean
  org.springframework.security.crypto.password.PasswordEncoder passwords;

  @org.springframework.boot.test.mock.mockito.SpyBean
  com.harvey.digitalgarden.tracker.TrackerActivityService trackerActivities;

  @org.springframework.boot.test.mock.mockito.SpyBean AccountLinks links;
  @MockBean AccountMailSender mail;
  final HttpClient http = HttpClient.newHttpClient();
  final Map<String, String> codes = new ConcurrentHashMap<>();

  record Reply(int status, JsonNode body, String cookie) {}

  @BeforeEach
  void setup() {
    for (String table :
        List.of("ledger_chat_message", "ledger_chat_attachment", "ledger_chat_draft"))
      db.execute("CREATE TABLE IF NOT EXISTS " + table + "(user_id BIGINT NOT NULL)");
    db.update("DELETE FROM site_account_rate");
    when(mail.configured()).thenReturn(true);
    doAnswer(
            i -> {
              codes.put(i.getArgument(0) + ":" + i.getArgument(1), i.getArgument(2));
              return null;
            })
        .when(mail)
        .sendCode(anyString(), anyString(), anyString());
  }

  Reply call(String method, String path, String cookie, Object body) {
    return call(method, path, cookie, body, "https://www.harvey404.com", Map.of());
  }

  Reply call(
      String method,
      String path,
      String cookie,
      Object body,
      String origin,
      Map<String, String> headers) {
    try {
      var b =
          HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
              .header("Content-Type", "application/json");
      if (cookie != null) b.header("Cookie", cookie);
      if (origin != null) b.header("Origin", origin);
      headers.forEach(b::header);
      var r =
          http.send(
              b.method(
                      method,
                      body == null
                          ? HttpRequest.BodyPublishers.noBody()
                          : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      JsonNode data;
      try {
        data = json.readTree(r.body());
      } catch (Exception e) {
        data = json.createObjectNode();
      }
      return new Reply(r.statusCode(), data, r.headers().firstValue("Set-Cookie").orElse(null));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  JsonNode ok(Reply r) {
    assertEquals(0, r.body.path("code").asInt(-1), r.body.toString() + " status=" + r.status);
    return r.body.path("data");
  }

  String email() {
    return "test-" + UUID.randomUUID() + "@example.com";
  }

  String code(String email, String purpose) {
    ok(call("POST", "/api/account/code", null, Map.of("email", email, "purpose", purpose)));
    assertNotNull(codes.get(email + ":" + purpose));
    return codes.get(email + ":" + purpose);
  }

  Reply register(String email) {
    return call(
        "POST",
        "/api/account/register",
        null,
        Map.of(
            "email",
            email,
            "password",
            "password123",
            "code",
            code(email, "register"),
            "timezone",
            "Asia/Shanghai",
            "displayName",
            "测试用户"));
  }

  String cookie(Reply r) {
    assertNotNull(r.cookie);
    return r.cookie.split(";", 2)[0];
  }

  @Test
  void verifiedRegistrationCookieAndModuleAccess() {
    String email = email();
    var r = register(email);
    var u = ok(r);
    String c = cookie(r);
    assertEquals(email, u.path("email").asText());
    assertTrue(r.cookie.contains("HttpOnly"));
    assertTrue(r.cookie.contains("Secure"));
    assertTrue(r.cookie.contains("Path=/"));
    assertTrue(r.cookie.contains("SameSite=Lax"));
    assertFalse(r.cookie.contains("Domain="));
    assertEquals(u.path("id"), ok(call("GET", "/api/account/me", c, null)).path("id"));
    ok(call("GET", "/api/tracker/auth/me", c, null));
    ok(call("GET", "/api/ledger/auth/me", c, null));
    var links = ok(call("GET", "/api/account/me", c, null)).path("links");
    assertTrue(links.path("tracker").asBoolean());
    assertTrue(links.path("ledger").asBoolean());
    var admin = call("GET", "/api/admin/posts", c, null);
    assertNotEquals(0, admin.body.path("code").asInt(-1));
    String hash =
        db.queryForObject(
            "SELECT password_hash FROM site_account WHERE email=?", String.class, email);
    assertNotEquals("password123", hash);
  }

  @Test
  void loginLogoutAndResetRevokeEverySession() {
    String email = email();
    String c = cookie(register(email));
    var logged =
        call(
            "POST",
            "/api/account/login",
            null,
            Map.of("email", email.toUpperCase(), "password", "password123"));
    ok(logged);
    String c2 = cookie(logged);
    db.update("UPDATE site_account_rate SET window_start=window_start-60001");
    String reset = code(email, "reset");
    ok(
        call(
            "POST",
            "/api/account/reset-password",
            null,
            Map.of("email", email, "password", "newpassword123", "code", reset)));
    assertEquals(401, call("GET", "/api/account/me", c, null).body.path("code").asInt());
    assertEquals(401, call("GET", "/api/account/me", c2, null).body.path("code").asInt());
    assertEquals(
        401,
        call("POST", "/api/account/login", null, Map.of("email", email, "password", "password123"))
            .body
            .path("code")
            .asInt());
    var next =
        call(
            "POST",
            "/api/account/login",
            null,
            Map.of("email", email, "password", "newpassword123"));
    ok(next);
    String c3 = cookie(next);
    ok(call("POST", "/api/account/logout", c3, Map.of()));
    assertEquals(401, call("GET", "/api/account/me", c3, null).body.path("code").asInt());
    assertNotEquals(
        0,
        call(
                "POST",
                "/api/account/reset-password",
                null,
                Map.of("email", email, "password", "badpassword123", "code", reset))
            .body
            .path("code")
            .asInt());
  }

  @Test
  void challengePurposeExpiryAndAttemptLimit() {
    String email = email(), c = code(email, "register");
    assertNotEquals(
        0,
        call(
                "POST",
                "/api/account/reset-password",
                null,
                Map.of("email", email, "password", "password123", "code", c))
            .body
            .path("code")
            .asInt());
    for (int i = 0; i < 5; i++)
      assertNotEquals(
          0,
          call(
                  "POST",
                  "/api/account/register",
                  null,
                  Map.of("email", email, "password", "password123", "code", "invalid"))
              .body
              .path("code")
              .asInt());
    assertNotEquals(
        0,
        call(
                "POST",
                "/api/account/register",
                null,
                Map.of("email", email, "password", "password123", "code", c))
            .body
            .path("code")
            .asInt());
    String expired = email(), ec = code(expired, "register");
    db.update("UPDATE site_email_challenge SET expires_at=0 WHERE email=?", expired);
    assertNotEquals(
        0,
        call(
                "POST",
                "/api/account/register",
                null,
                Map.of("email", expired, "password", "password123", "code", ec))
            .body
            .path("code")
            .asInt());
  }

  @Test
  void crossOriginAndUnconfiguredMailFailClearly() {
    var rejected =
        call(
            "POST",
            "/api/account/code",
            null,
            Map.of("email", email(), "purpose", "register"),
            "https://evil.example",
            Map.of());
    assertEquals(403, rejected.status);
    assertEquals(
        403,
        call(
                "POST",
                "/api/account/code",
                null,
                Map.of("email", email(), "purpose", "register"),
                null,
                Map.of())
            .status);
    when(mail.configured()).thenReturn(false);
    assertFalse(
        ok(call("GET", "/api/account/status", null, null)).path("mailConfigured").asBoolean());
    assertEquals(
        503,
        call("POST", "/api/account/code", null, Map.of("email", email(), "purpose", "register"))
            .body
            .path("code")
            .asInt());
  }

  @Test
  void codeRateLimitIsPersistentAndCodeNotExposed() {
    String email = email();
    var first =
        call("POST", "/api/account/code", null, Map.of("email", email, "purpose", "register"));
    ok(first);
    assertFalse(first.body.toString().contains(codes.get(email + ":register")));
    assertEquals(
        429,
        call("POST", "/api/account/code", null, Map.of("email", email, "purpose", "register"))
            .body
            .path("code")
            .asInt());
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM site_email_challenge WHERE email=?", Integer.class, email));
  }

  @Test
  void oldTrackerRecordsStayOnTheirOriginalIdentityAndCannotBeClaimedTwice() {
    String name = "old_" + UUID.randomUUID().toString().substring(0, 10);
    db.update(
        "INSERT INTO tracker_user(username,password_hash,timezone) VALUES(?,?,?)",
        name,
        passwords.encode("password123"),
        "Asia/Shanghai");
    long uid = db.queryForObject("SELECT id FROM tracker_user WHERE username=?", Long.class, name);
    String token = jwt.generateTrackerToken(uid, name);
    db.update(
        "INSERT INTO"
            + " tracker_activity(user_id,name,active_name,unit,template,track_duration,archived)"
            + " VALUES(?,?,?,?,?,?,?)",
        uid,
        "历史目标",
        "历史目标",
        "次",
        "custom",
        false,
        false);
    String c = cookie(register(email()));
    ok(call("POST", "/api/account/links/tracker", c, Map.of("token", token)));
    assertEquals(uid, ok(call("GET", "/api/tracker/auth/me", c, null)).path("id").asLong());
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM tracker_activity WHERE user_id=?", Integer.class, uid));
    String other = cookie(register(email()));
    assertEquals(
        409,
        call(
                "POST",
                "/api/account/links/tracker",
                other,
                Map.of("username", name, "password", "password123"))
            .body
            .path("code")
            .asInt());
    var boundLegacy =
        call(
            "GET",
            "/api/tracker/auth/me",
            null,
            null,
            "https://www.harvey404.com",
            Map.of("Authorization", "Bearer " + token));
    assertNotEquals(0, boundLegacy.body.path("code").asInt(-1));
    assertNotEquals(
        0,
        call(
                "POST",
                "/api/tracker/auth/login",
                null,
                Map.of("username", name, "password", "password123"))
            .body
            .path("code")
            .asInt());
  }

  @Test
  void oldLedgerInvitePreservesBudgetAndExpenseIdentity() {
    String invite = "ABCD-EFGH-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    String sn = "legacy-" + UUID.randomUUID();
    long now = System.currentTimeMillis();
    db.update(
        "INSERT INTO"
            + " ledger_user(user_sn,invite_code,display_name,default_budget_amount,status,is_deleted,in_dtm,update_dtm)"
            + " VALUES(?,?,?,3000,'active',0,?,?)",
        sn,
        invite,
        "原账本",
        now,
        now);
    long uid = db.queryForObject("SELECT id FROM ledger_user WHERE user_sn=?", Long.class, sn);
    String c = cookie(register(email()));
    ok(call("POST", "/api/account/links/ledger", c, Map.of("inviteCode", invite)));
    assertEquals(
        "原账本", ok(call("GET", "/api/ledger/auth/me", c, null)).path("displayName").asText());
    assertEquals(
        uid,
        db.queryForObject(
            "SELECT local_user_id FROM site_account_link WHERE module='ledger' AND"
                + " account_id=(SELECT id FROM site_account WHERE email=?)",
            Long.class,
            ok(call("GET", "/api/account/me", c, null)).path("email").asText()));
    assertNotEquals(
        0,
        call("POST", "/api/ledger/auth/invite", null, Map.of("inviteCode", invite))
            .body
            .path("code")
            .asInt());
  }

  @Test
  void internalIntrospectionRequiresServiceSecretAndRealSession() {
    String c = cookie(register(email())), token = c.substring(c.indexOf('=') + 1);
    assertEquals(
        403,
        call(
                "GET",
                "/api/account/internal/session",
                null,
                null,
                null,
                Map.of("X-Site-Session", token))
            .status);
    var r =
        call(
            "GET",
            "/api/account/internal/session",
            null,
            null,
            null,
            Map.of(
                "X-Site-Service-Key",
                "test-site-service-key-with-at-least-32-bytes",
                "X-Site-Session",
                token));
    ok(r);
    assertEquals(
        401,
        call(
                "GET",
                "/api/account/internal/session",
                null,
                null,
                null,
                Map.of(
                    "X-Site-Service-Key",
                    "test-site-service-key-with-at-least-32-bytes",
                    "X-Site-Session",
                    "invalid"))
            .status);
  }

  @Test
  void usersHaveDifferentModuleIdentitiesAndExpiredSessionsFailClosed() {
    String one = cookie(register(email())), two = cookie(register(email()));
    long a = ok(call("GET", "/api/tracker/auth/me", one, null)).path("id").asLong(),
        b = ok(call("GET", "/api/tracker/auth/me", two, null)).path("id").asLong();
    assertNotEquals(a, b);
    db.update("UPDATE site_session SET expires_at=0");
    assertNotEquals(0, call("GET", "/api/tracker/auth/me", one, null).body.path("code").asInt(-1));
  }

  @Test
  void legacyRegistrationCannotBypassEmailVerification() {
    assertEquals(
        410,
        call(
                "POST",
                "/api/tracker/auth/register",
                null,
                Map.of(
                    "username",
                    "unverified",
                    "password",
                    "password123",
                    "timezone",
                    "Asia/Shanghai"))
            .body
            .path("code")
            .asInt());
  }

  @Test
  void invalidSiteCookieNeverFallsBackToValidLegacyBearer() {
    String name = "old_" + UUID.randomUUID().toString().substring(0, 10);
    db.update(
        "INSERT INTO tracker_user(username,password_hash,timezone) VALUES(?,?,?)",
        name,
        passwords.encode("password123"),
        "Asia/Shanghai");
    long uid = db.queryForObject("SELECT id FROM tracker_user WHERE username=?", Long.class, name);
    assertEquals(
        401,
        call(
                "GET",
                "/api/tracker/auth/me",
                "dg_session=invalid",
                null,
                "https://www.harvey404.com",
                Map.of("Authorization", "Bearer " + jwt.generateTrackerToken(uid, name)))
            .body
            .path("code")
            .asInt());
  }

  @Test
  void populatedTrackerCannotBeSilentlyReplacedAndBindingIsIdempotent() {
    String c = cookie(register(email()));
    long current = ok(call("GET", "/api/tracker/auth/me", c, null)).path("id").asLong();
    db.update(
        "INSERT INTO"
            + " tracker_activity(user_id,name,active_name,unit,template,track_duration,archived)"
            + " VALUES(?,?,?,?,?,?,?)",
        current,
        "新记录",
        "新记录",
        "次",
        "custom",
        false,
        false);
    String name = "old_" + UUID.randomUUID().toString().substring(0, 10);
    db.update(
        "INSERT INTO tracker_user(username,password_hash,timezone) VALUES(?,?,?)",
        name,
        passwords.encode("password123"),
        "Asia/Shanghai");
    long legacy =
        db.queryForObject("SELECT id FROM tracker_user WHERE username=?", Long.class, name);
    assertEquals(
        409,
        call(
                "POST",
                "/api/account/links/tracker",
                c,
                Map.of("username", name, "password", "password123"))
            .body
            .path("code")
            .asInt());
    assertEquals(current, ok(call("GET", "/api/tracker/auth/me", c, null)).path("id").asLong());
    String other = cookie(register(email()));
    ok(
        call(
            "POST",
            "/api/account/links/tracker",
            other,
            Map.of("username", name, "password", "password123")));
    ok(
        call(
            "POST",
            "/api/account/links/tracker",
            other,
            Map.of("token", jwt.generateTrackerToken(legacy, name))));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM tracker_activity WHERE user_id=?", Integer.class, current));
  }

  @Test
  void emptyProvisionedTrackerMayBindOldIdentityWithoutDeletingAnyRows() {
    String c = cookie(register(email()));
    long auto = ok(call("GET", "/api/tracker/auth/me", c, null)).path("id").asLong();
    String name = "old_" + UUID.randomUUID().toString().substring(0, 10);
    db.update(
        "INSERT INTO tracker_user(username,password_hash,timezone) VALUES(?,?,?)",
        name,
        passwords.encode("password123"),
        "UTC");
    long old = db.queryForObject("SELECT id FROM tracker_user WHERE username=?", Long.class, name);
    ok(
        call(
            "POST",
            "/api/account/links/tracker",
            c,
            Map.of("username", name, "password", "password123")));
    assertEquals(old, ok(call("GET", "/api/tracker/auth/me", c, null)).path("id").asLong());
    assertEquals(
        1, db.queryForObject("SELECT COUNT(*) FROM tracker_user WHERE id=?", Integer.class, auto));
  }

  @Test
  void smtpFailureDoesNotCreateAUsableVerificationChallenge() {
    String email = email();
    doThrow(new com.harvey.digitalgarden.common.BusinessException(503, "邮件暂时发送失败"))
        .when(mail)
        .sendCode(eq(email), eq("register"), anyString());
    assertEquals(
        503,
        call("POST", "/api/account/code", null, Map.of("email", email, "purpose", "register"))
            .body
            .path("code")
            .asInt());
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM site_email_challenge WHERE email=?", Integer.class, email));
  }

  @Test
  void bindingWaitsForInFlightWriteAndRetainsItsNewRecord() throws Exception {
    String c = cookie(register(email()));
    long current = ok(call("GET", "/api/tracker/auth/me", c, null)).path("id").asLong();
    String name = "old_" + UUID.randomUUID().toString().substring(0, 10);
    db.update(
        "INSERT INTO tracker_user(username,password_hash,timezone) VALUES(?,?,?)",
        name,
        passwords.encode("password123"),
        "UTC");
    CountDownLatch entered = new CountDownLatch(1),
        release = new CountDownLatch(1),
        binding = new CountDownLatch(1);
    doAnswer(
            i -> {
              entered.countDown();
              assertTrue(release.await(5, TimeUnit.SECONDS));
              return i.callRealMethod();
            })
        .when(trackerActivities)
        .create(eq(current), any());
    doAnswer(
            i -> {
              binding.countDown();
              return i.callRealMethod();
            })
        .when(links)
        .bind(any(), eq("tracker"), any(), any(), any(), any());
    Map<String, Object> activity =
        Map.of(
            "requestId",
            UUID.randomUUID().toString(),
            "name",
            "并发记录",
            "unit",
            "次",
            "template",
            "custom",
            "trackDuration",
            false,
            "rule",
            Map.of("period", "DAY", "metric", "COUNT", "direction", "MIN", "target", 1));
    var write =
        CompletableFuture.supplyAsync(() -> call("POST", "/api/tracker/activities", c, activity));
    try {
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      var bind =
          CompletableFuture.supplyAsync(
              () ->
                  call(
                      "POST",
                      "/api/account/links/tracker",
                      c,
                      Map.of("username", name, "password", "password123")));
      assertTrue(binding.await(5, TimeUnit.SECONDS));
      assertThrows(TimeoutException.class, () -> bind.get(200, TimeUnit.MILLISECONDS));
      release.countDown();
      ok(write.get(5, TimeUnit.SECONDS));
      assertEquals(409, bind.get(5, TimeUnit.SECONDS).body.path("code").asInt());
      assertEquals(current, ok(call("GET", "/api/tracker/auth/me", c, null)).path("id").asLong());
      assertEquals(
          1,
          db.queryForObject(
              "SELECT COUNT(*) FROM tracker_activity WHERE user_id=?", Integer.class, current));
    } finally {
      release.countDown();
    }
  }

  @Test
  void ledgerProfileAndCustomCategoriesPreventReplacingIdentity() {
    String c = cookie(register(email()));
    ok(call("GET", "/api/ledger/auth/me", c, null));
    long id = ok(call("GET", "/api/account/me", c, null)).path("id").asLong();
    long uid =
        db.queryForObject(
            "SELECT local_user_id FROM site_account_link WHERE account_id=? AND module='ledger'",
            Long.class,
            id);
    String invite = "ABCD-EFGH-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    String sn = "legacy-" + UUID.randomUUID();
    db.update(
        "INSERT INTO"
            + " ledger_user(user_sn,invite_code,display_name,default_budget_amount,status,is_deleted,in_dtm,update_dtm)"
            + " VALUES(?,?,'原账本',3000,'active',0,0,0)",
        sn,
        invite);
    db.update(
        "UPDATE ledger_user SET display_name='新设置',default_budget_amount=1234 WHERE id=?", uid);
    assertEquals(
        409,
        call("POST", "/api/account/links/ledger", c, Map.of("inviteCode", invite))
            .body
            .path("code")
            .asInt());
    db.update("UPDATE ledger_user SET display_name='',default_budget_amount=0 WHERE id=?", uid);
    db.update("UPDATE ledger_category SET name='我的自定义分类' WHERE user_id=? AND sort_order=0", uid);
    assertEquals(
        409,
        call("POST", "/api/account/links/ledger", c, Map.of("inviteCode", invite))
            .body
            .path("code")
            .asInt());
    assertEquals(
        "我的自定义分类",
        db.queryForObject(
            "SELECT name FROM ledger_category WHERE user_id=? AND sort_order=0",
            String.class,
            uid));
  }

  @Test
  void racingOldPasswordLoginCannotLeaveSessionAfterPasswordReset() throws Exception {
    String email = email();
    cookie(register(email));
    db.update("UPDATE site_account_rate SET window_start=window_start-60001");
    String resetCode = code(email, "reset");
    CountDownLatch checking = new CountDownLatch(1), release = new CountDownLatch(1);
    doAnswer(
            i -> {
              checking.countDown();
              assertTrue(release.await(5, TimeUnit.SECONDS));
              return i.callRealMethod();
            })
        .when(passwords)
        .matches(eq("password123"), anyString());
    var login =
        CompletableFuture.supplyAsync(
            () ->
                call(
                    "POST",
                    "/api/account/login",
                    null,
                    Map.of("email", email, "password", "password123")));
    try {
      assertTrue(checking.await(5, TimeUnit.SECONDS));
      var reset =
          CompletableFuture.supplyAsync(
              () ->
                  call(
                      "POST",
                      "/api/account/reset-password",
                      null,
                      Map.of("email", email, "password", "newpassword123", "code", resetCode)));
      try {
        reset.get(200, TimeUnit.MILLISECONDS);
      } catch (TimeoutException expected) {
      }
      release.countDown();
      var logged = login.get(5, TimeUnit.SECONDS);
      ok(logged);
      ok(reset.get(5, TimeUnit.SECONDS));
      assertEquals(
          401, call("GET", "/api/account/me", cookie(logged), null).body.path("code").asInt());
    } finally {
      release.countDown();
    }
  }

  @Test
  void requestSeesBindingCommittedAfterSessionAuthentication() throws Exception {
    String c = cookie(register(email()));
    ok(call("GET", "/api/tracker/auth/me", c, null));
    long account = ok(call("GET", "/api/account/me", c, null)).path("id").asLong();
    String name = "old_" + UUID.randomUUID().toString().substring(0, 10);
    db.update(
        "INSERT INTO tracker_user(username,password_hash,timezone) VALUES(?,?,?)",
        name,
        passwords.encode("password123"),
        "UTC");
    long target =
        db.queryForObject("SELECT id FROM tracker_user WHERE username=?", Long.class, name);
    CountDownLatch changed = new CountDownLatch(1),
        commit = new CountDownLatch(1),
        authenticated = new CountDownLatch(1),
        resolve = new CountDownLatch(1);
    var binding =
        CompletableFuture.runAsync(
            () ->
                new org.springframework.transaction.support.TransactionTemplate(transactions)
                    .executeWithoutResult(
                        status -> {
                          db.queryForObject(
                              "SELECT id FROM site_account WHERE id=? FOR UPDATE",
                              Long.class,
                              account);
                          db.update(
                              "UPDATE site_account_link SET local_user_id=? WHERE account_id=? AND"
                                  + " module='tracker'",
                              target,
                              account);
                          changed.countDown();
                          try {
                            assertTrue(commit.await(5, TimeUnit.SECONDS));
                          } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                          }
                        }));
    try {
      assertTrue(changed.await(5, TimeUnit.SECONDS));
      doAnswer(
              i -> {
                authenticated.countDown();
                assertTrue(resolve.await(5, TimeUnit.SECONDS));
                return i.callRealMethod();
              })
          .when(links)
          .resolve(any(), eq("tracker"));
      var request =
          CompletableFuture.supplyAsync(() -> call("GET", "/api/tracker/auth/me", c, null));
      assertTrue(authenticated.await(5, TimeUnit.SECONDS));
      commit.countDown();
      binding.get(5, TimeUnit.SECONDS);
      resolve.countDown();
      assertEquals(target, ok(request.get(5, TimeUnit.SECONDS)).path("id").asLong());
    } finally {
      commit.countDown();
      resolve.countDown();
    }
  }

  @Test
  void trackerTimezoneSettingPreventsReplacingIdentity() {
    String c = cookie(register(email()));
    long current = ok(call("GET", "/api/tracker/auth/me", c, null)).path("id").asLong();
    ok(call("PUT", "/api/tracker/auth/me", c, Map.of("timezone", "UTC")));
    String name = "old_" + UUID.randomUUID().toString().substring(0, 10);
    db.update(
        "INSERT INTO tracker_user(username,password_hash,timezone) VALUES(?,?,?)",
        name,
        passwords.encode("password123"),
        "Asia/Shanghai");
    assertEquals(
        409,
        call(
                "POST",
                "/api/account/links/tracker",
                c,
                Map.of("username", name, "password", "password123"))
            .body
            .path("code")
            .asInt());
    var after = ok(call("GET", "/api/tracker/auth/me", c, null));
    assertEquals(current, after.path("id").asLong());
    assertEquals("UTC", after.path("timezone").asText());
  }
}
