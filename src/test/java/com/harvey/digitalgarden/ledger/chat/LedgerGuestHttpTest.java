package com.harvey.digitalgarden.ledger.chat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.harvey.digitalgarden.account.AccountMailSender;
import com.harvey.digitalgarden.ledger.chat.ai.LedgerChatModel;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.datasource.url=jdbc:h2:mem:ledgerguest;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false", "app.account.origin=https://www.harvey404.com", "app.account.secure-cookie=true",
    "app.ledger-chat.private-dir=./target/guest-test-private"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LedgerGuestHttpTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired com.harvey.digitalgarden.service.ledger.LedgerUserAdminService ledgerUsers;
    @MockBean LedgerChatModel model;
    @MockBean AccountMailSender mail;
    final HttpClient http=HttpClient.newHttpClient();
    record Reply(int status, JsonNode body, String cookie) {}

    @BeforeAll void schema() throws Exception {
        for(String file:List.of("V7__ledger_chat.sql","V8__behavior_tracker.sql","V9__unified_account.sql","V10__ledger_guest.sql")) {
            var resource=new org.springframework.core.io.ClassPathResource("db/migration/"+file);
            if(!resource.exists()) continue;
            String sql=resource.getContentAsString(StandardCharsets.UTF_8);
            if(file.startsWith("V7"))sql=sql.substring(sql.indexOf(';')+1);
            for(String statement:sql.split(";"))if(!statement.isBlank())db.execute(statement);
        }
    }
    @BeforeEach void model() throws Exception {
        reset(model); when(model.available()).thenReturn(true);
        when(model.parse(any())).thenReturn(json.readTree("{\"action\":\"CLARIFY\",\"reply\":\"完整的助手回复\"}"));
        db.update("DELETE FROM site_account_rate");
    }
    Reply call(String method,String path,String cookie,Object body) { return call(method,path,cookie,body,"https://www.harvey404.com"); }
    Reply call(String method,String path,String cookie,Object body,String origin) {
        return call(method,path,cookie,body,origin,Map.of());
    }
    Reply call(String method,String path,String cookie,Object body,String origin,Map<String,String> headers) {
        try {
            var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).header("Content-Type","application/json");
            if(cookie!=null)b.header("Cookie",cookie); if(origin!=null)b.header("Origin",origin);
            headers.forEach(b::header);
            var r=http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
            JsonNode bodyJson;try {bodyJson=json.readTree(r.body());}catch(Exception ignored){bodyJson=json.createObjectNode();}
            return new Reply(r.statusCode(),bodyJson,r.headers().firstValue("Set-Cookie").orElse(null));
        } catch(Exception e) { throw new RuntimeException(e); }
    }
    JsonNode ok(Reply r) { assertEquals(0,r.body.path("code").asInt(-1),r.body.toString());return r.body.path("data"); }
    String visitor() {
        var r=call("GET","/api/ledger/chat/session",null,null);var data=ok(r);
        assertTrue(data.path("isGuest").asBoolean()); assertEquals(3,data.path("remaining").asInt());
        assertNotNull(r.cookie);assertTrue(r.cookie.startsWith("dg_guest_ledger="));
        assertTrue(r.cookie.contains("HttpOnly"));assertTrue(r.cookie.contains("Secure"));assertTrue(r.cookie.contains("SameSite=Lax"));assertTrue(r.cookie.contains("Path=/"));
        return r.cookie.split(";",2)[0];
    }
    Reply send(String cookie,String id) { return call("POST","/api/ledger/chat/messages",cookie,Map.of("requestId",id,"text","咖啡多少钱","attachmentIds",List.of())); }
    long uid(String cookie) { return db.queryForObject("SELECT user_id FROM ledger_guest_session WHERE token_hash=?",Long.class,LedgerChatService.sha256(cookie.substring(cookie.indexOf('=')+1).getBytes(StandardCharsets.UTF_8))); }
    String account() {
        String email="guest-"+UUID.randomUUID()+"@example.com";
        db.update("INSERT INTO site_account(email,password_hash,display_name,timezone,status,created_at) VALUES(?,?,?,'Asia/Shanghai','active',?)",email,passwords.encode("password123"),"邮箱用户",System.currentTimeMillis());
        var r=call("POST","/api/account/login",null,Map.of("email",email,"password","password123"));ok(r);return r.cookie.split(";",2)[0];
    }
    @Test void threeCompleteRepliesThenFourthRequiresLoginAndHistorySurvivesRefresh() {
        String c=visitor();
        for(int i=1;i<=3;i++) { assertEquals("完整的助手回复",ok(send(c,"round-"+i)).path("assistantMessage").path("text").asText());assertEquals(3-i,ok(call("GET","/api/ledger/chat/session",c,null)).path("remaining").asInt()); }
        assertEquals(403,send(c,"fourth").body.path("code").asInt());
        var history=ok(call("GET","/api/ledger/chat/history",c,null));assertEquals(6,history.path("messages").size());assertEquals(0,history.path("guest").path("remaining").asInt());
        assertEquals(3,db.queryForObject("SELECT completed FROM ledger_guest_session WHERE user_id=?",Integer.class,uid(c)));
        verify(model,times(3)).parse(any());
    }
    @Test void sameRequestRetryDoesNotChargeOrCallModelAgainEvenAfterExhaustion() {
        String c=visitor();for(int i=1;i<=3;i++)ok(send(c,"same-"+i));
        when(model.available()).thenReturn(false);
        assertEquals("完整的助手回复",ok(send(c,"same-1")).path("assistantMessage").path("text").asText());
        assertEquals(403,send(c,"offline-fourth").body.path("code").asInt());
        assertEquals(0,ok(call("GET","/api/ledger/chat/session",c,null)).path("remaining").asInt());verify(model,times(3)).parse(any());
    }
    @Test void failedModelAndValidationDoNotConsumeAndSameRequestCanRetry() throws Exception {
        String c=visitor();when(model.parse(any())).thenThrow(new IllegalStateException("upstream failed"));
        assertNotEquals(0,send(c,"retry").body.path("code").asInt());
        assertEquals(3,ok(call("GET","/api/ledger/chat/session",c,null)).path("remaining").asInt());
        doReturn(json.readTree("{\"action\":\"CLARIFY\",\"reply\":\"成功重试\"}")).when(model).parse(any());ok(send(c,"retry"));
        assertEquals(2,ok(call("GET","/api/ledger/chat/session",c,null)).path("remaining").asInt());
    }
    @Test void concurrentSubmitsCannotPassQuotaAndModelDoesNotHoldDatabaseTransaction() throws Exception {
        String c=visitor();ok(send(c,"one"));ok(send(c,"two"));
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        when(model.parse(any())).thenAnswer(i->{entered.countDown();assertTrue(release.await(8,TimeUnit.SECONDS));return json.readTree("{\"action\":\"CLARIFY\",\"reply\":\"第三轮完整回复\"}");});
        var first=CompletableFuture.supplyAsync(()->send(c,"three"));
        try {
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            var second=CompletableFuture.supplyAsync(()->send(c,"concurrent"));
            assertEquals(409,second.get(3,TimeUnit.SECONDS).body.path("code").asInt());
            assertEquals(4,ok(call("GET","/api/ledger/chat/history",c,null)).path("messages").size()-1);
        } finally {release.countDown();}
        ok(first.get(5,TimeUnit.SECONDS));assertEquals(403,send(c,"four").body.path("code").asInt());
    }
    @Test void staleLeaseRecoversWithoutCharge() {
        String c=visitor();long id=uid(c);ok(call("GET","/api/ledger/chat/history",c,null));
        db.update("UPDATE ledger_chat_session SET processing_request_id='stale',processing_attempt='dead',processing_until=1 WHERE user_id=?",id);
        ok(send(c,"recovered"));assertEquals(2,ok(call("GET","/api/ledger/chat/session",c,null)).path("remaining").asInt());
    }
    @Test void crossOriginMissingOriginAndInvalidSiteLoginFailClosed() {
        String c=visitor();var input=Map.of("requestId","csrf","text","test","attachmentIds",List.of());
        assertEquals(403,call("POST","/api/ledger/chat/messages",c,input,"https://evil.example").status);
        assertEquals(403,call("POST","/api/ledger/chat/messages",c,input,null).status);
        assertEquals(401,call("GET","/api/ledger/chat/history","dg_session=invalid; "+c,null).body.path("code").asInt());
        assertEquals(401,call("GET","/api/ledger/chat/history","dg_guest_ledger=forged",null).body.path("code").asInt());
    }
    @Test void guestIsIsolatedAndCannotReachProtectedBusinessOrAccountData() {
        String a=visitor(),b=visitor();ok(send(a,"private"));
        assertEquals(0,ok(call("GET","/api/ledger/chat/history",b,null)).path("messages").size());
        for(String path:List.of("/api/ledger/expenses","/api/ledger/budgets","/api/ledger/auth/me","/api/account/me","/api/admin/posts","/api/tracker/activities"))assertNotEquals(0,call("GET",path,a,null).body.path("code").asInt(-1),path);
        assertEquals(403,call("POST","/api/ledger/chat/drafts/1/confirm",a,Map.of("expectedVersion",1)).body.path("code").asInt());
    }
    @Test void loginMigrationPreservesPriorAccountHistoryAndRetiresGuestCredential() {
        String guest=visitor();ok(send(guest,"guest-history"));long old=uid(guest);
        String account=account();ok(call("GET","/api/ledger/chat/session",account,null));ok(send(account,"account-history"));
        var both=ok(call("GET","/api/ledger/chat/history",account+"; "+guest,null));assertEquals(4,both.path("messages").size());
        assertEquals("guest:"+old,both.path("guest").path("adoptedGuestKey").asText());
        assertTrue(both.path("guest").path("identityKey").asText().startsWith("ledger:"));
        String unrelated=account();
        assertTrue(ok(call("GET","/api/ledger/chat/history",unrelated+"; "+guest,null)).path("guest").path("adoptedGuestKey").isNull());
        assertEquals(4,ok(call("GET","/api/ledger/chat/history",account+"; "+guest,null)).path("messages").size());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ledger_chat_message WHERE user_id=?",Integer.class,old));
        ok(call("POST","/api/account/logout",account,Map.of()));
        assertEquals(401,call("GET","/api/ledger/chat/history",guest,null).body.path("code").asInt());
    }
    @Test void authenticatedAccountBypassesGuestQuota() {
        String c=account();ok(call("GET","/api/ledger/chat/session",c,null));for(int i=0;i<4;i++)ok(send(c,"signed-"+i));
        assertFalse(ok(call("GET","/api/ledger/chat/session",c,null)).path("isGuest").asBoolean());
    }
    @Test void unavailableInvalidOrIncompleteModelReplyDoesNotCharge() throws Exception {
        String c=visitor();when(model.available()).thenReturn(false);
        assertNotEquals(0,send(c,"unavailable").body.path("code").asInt());when(model.available()).thenReturn(true);
        assertNotEquals(0,call("POST","/api/ledger/chat/messages",c,Map.of("requestId","invalid","text","","attachmentIds",List.of())).body.path("code").asInt());
        when(model.parse(any())).thenReturn(json.readTree("{\"action\":\"CLARIFY\",\"reply\":\"\"}"));
        assertNotEquals(0,send(c,"incomplete").body.path("code").asInt());
        assertEquals(3,ok(call("GET","/api/ledger/chat/session",c,null)).path("remaining").asInt());
    }
    @Test void crossSiteBootstrapCannotCreateGuestAndProvisioningIsRateLimited() {
        assertEquals(403,call("GET","/api/ledger/chat/session",null,null,null,Map.of("Sec-Fetch-Site","cross-site")).status);
        assertEquals(403,call("GET","/api/ledger/chat/session",null,null,"https://evil.example").status);
        for(int i=0;i<20;i++)visitor();
        assertEquals(429,call("GET","/api/ledger/chat/session",null,null).status);
    }
    @Test void accountBindingDuringModelDoesNotOrphanReservedMessagesOrHoldAccountLock() throws Exception {
        String c=account();ok(call("GET","/api/ledger/chat/session",c,null));
        var legacy=ledgerUsers.createUser();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        when(model.parse(any())).thenAnswer(i->{
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            entered.countDown();assertTrue(release.await(8,TimeUnit.SECONDS));return json.readTree("{\"action\":\"CLARIFY\",\"reply\":\"完整回复\"}");
        });
        var response=CompletableFuture.supplyAsync(()->send(c,"during-model"));
        try {
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            var binding=CompletableFuture.supplyAsync(()->call("POST","/api/account/links/ledger",c,Map.of("inviteCode",legacy.getInviteCode())));
            assertEquals(409,binding.get(3,TimeUnit.SECONDS).body.path("code").asInt());
        }finally{release.countDown();}
        ok(response.get(5,TimeUnit.SECONDS));assertEquals(2,ok(call("GET","/api/ledger/chat/history",c,null)).path("messages").size());
    }
    @Test void migrationPreservesBothDraftsAttachmentsCategoriesAndCollidingRequestIds() throws Exception {
        String g=visitor();long source=uid(g);String account=account();ok(call("GET","/api/ledger/chat/session",account,null));
        long target=ok(call("GET","/api/ledger/auth/me",account,null)).path("id").asLong();
        if(target==0)target=db.queryForObject("SELECT local_user_id FROM site_account_link WHERE account_id=(SELECT account_id FROM site_session WHERE token_hash=?) AND module='ledger'",Long.class,LedgerChatService.sha256(account.substring(account.indexOf('=')+1).getBytes(StandardCharsets.UTF_8)));
        long sourceCategory=db.queryForObject("SELECT MIN(id) FROM ledger_category WHERE user_id=?",Long.class,source);
        long targetCategory=db.queryForObject("SELECT MIN(id) FROM ledger_category WHERE user_id=?",Long.class,target);
        when(model.parse(any())).thenReturn(json.readTree("{\"action\":\"APPEND\",\"reply\":\"草稿\",\"items\":[{\"amount\":\"10\",\"categoryId\":"+sourceCategory+",\"description\":\"游客咖啡\"}]}"));
        long guestDraft=ok(send(g,"collision")).path("draft").path("id").asLong();
        db.update("INSERT INTO ledger_chat_attachment(user_id,hash,storage_key,content_type,width,height,in_dtm) VALUES(?,'hash','guest-file','image/png',1,1,1)",source);
        when(model.parse(any())).thenReturn(json.readTree("{\"action\":\"APPEND\",\"reply\":\"草稿\",\"items\":[{\"amount\":\"20\",\"categoryId\":"+targetCategory+",\"description\":\"账号午饭\"}]}"));
        long priorDraft=ok(send(account,"collision")).path("draft").path("id").asLong();
        var migrated=ok(call("GET","/api/ledger/chat/history",account+"; "+g,null));
        assertEquals(4,migrated.path("messages").size());assertEquals(2,migrated.path("drafts").size());
        assertEquals(priorDraft,migrated.path("activeDraft").path("id").asLong());
        assertEquals(target,db.queryForObject("SELECT user_id FROM ledger_chat_draft WHERE id=?",Long.class,guestDraft));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM ledger_chat_attachment WHERE user_id=?",Integer.class,target));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM ledger_chat_draft_item WHERE user_id=?",Integer.class,source));
        var item=json.readTree(db.queryForObject("SELECT data_json FROM ledger_chat_draft_item WHERE draft_id=?",String.class,guestDraft));
        assertEquals(targetCategory,item.path("categoryId").asLong());
        var confirmed=ok(call("POST","/api/ledger/chat/drafts/"+guestDraft+"/confirm",account,Map.of("expectedVersion",2)));
        assertEquals("CONFIRMED",confirmed.path("status").asText());
    }
}
