package com.harvey.digitalgarden.ledger.chat;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*;
import com.harvey.digitalgarden.security.JwtUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import com.harvey.digitalgarden.ledger.chat.ai.LedgerChatModel;
import com.harvey.digitalgarden.service.ledger.LedgerExpenseService;
import com.harvey.digitalgarden.common.BusinessException;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("chat-test")
class ChatFlowIT {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwt;
    @Autowired ObjectMapper json;
    @MockBean LedgerChatModel model;
    @SpyBean LedgerExpenseService expenseService;
    String token;
    @BeforeEach void seed() {
        assertEquals("ledger_chat_test", jdbc.queryForObject("SELECT DATABASE()",String.class));
        for (String table : new String[]{"ledger_chat_expense_link","ledger_chat_draft_item","ledger_chat_message","ledger_chat_draft","ledger_chat_attachment","ledger_chat_session","ledger_expense","ledger_budget","ledger_category","ledger_user"})
            jdbc.update("DELETE FROM " + table);
        jdbc.update("INSERT INTO ledger_user(id,user_sn,invite_code,display_name,default_budget_amount) VALUES(101,'test101','TEST-0000-0001','测试用户',3000),(202,'test202','TEST-0000-0002','另一用户',3000)");
        jdbc.update("INSERT INTO ledger_category(id,user_id,name) VALUES(1001,101,'餐饮'),(1002,101,'交通'),(2001,202,'餐饮')");
        token=jwt.generateLedgerToken(101,"test101");
        when(model.available()).thenReturn(true);
    }
    JsonNode call(String method,String path,String body) throws Exception {
        var request = switch(method) {case "POST"->post(path);case "PUT"->put(path);case "DELETE"->delete(path);default->get(path);};
        String result=mvc.perform(request.header("Authorization","Bearer "+token)
            .contentType("application/json").content(body==null?"":body)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return json.readTree(result);
    }
    @Test void historyIsAnOwnedEmptyConversation() throws Exception {
        JsonNode r=call("GET","/api/ledger/chat/history",null);
        assertEquals(0,r.path("code").asInt(-1));
        assertTrue(r.path("data").path("messages").isArray());
        assertEquals(0,r.path("data").path("messages").size());
    }
    JsonNode createDraft() throws Exception {
        when(model.parse(any())).thenReturn(json.readTree("""
            {"action":"APPEND","reply":"请核对两笔消费。","items":[
              {"amount":"32.50","categoryId":1001,"description":"午饭","date":"2026-10-06"},
              {"amount":"18","categoryId":1002,"description":"打车","date":"2026-09-30"}]}
            """));
        var r=call("POST","/api/ledger/chat/messages","{\"requestId\":\"request-1\",\"text\":\"午饭32.5，打车18\",\"attachmentIds\":[],\"expectedDraftVersion\":null}");
        assertEquals(0,r.path("code").asInt(-1),r.toString());
        return r.path("data").path("draft");
    }
    @Test void draftDoesNotCreateExpensesAndConfirmationIsIdempotent() throws Exception {
        var d=createDraft();
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ledger_expense",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ledger_budget",Integer.class));
        assertEquals("50.50",d.path("total").asText());
        String path="/api/ledger/chat/drafts/"+d.path("id").asLong()+"/confirm";
        String version="{\"expectedVersion\":"+d.path("version").asLong()+"}";
        var first=call("POST",path,version);
        var second=call("POST",path,version);
        assertEquals("CONFIRMED",first.path("data").path("status").asText(),first.toString());
        assertEquals(first.path("data").path("savedExpenses"),second.path("data").path("savedExpenses"));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM ledger_expense WHERE is_deleted=0",Integer.class));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM ledger_budget",Integer.class));
    }
    @Test void failedSecondInsertRollsBackEntireBatchAndBudgets() throws Exception {
        var d=createDraft();
        doCallRealMethod().doThrow(BusinessException.badRequest("模拟第二笔保存失败")).when(expenseService).createExpense(eq(101L),any());
        var result=call("POST","/api/ledger/chat/drafts/"+d.path("id").asLong()+"/confirm","{\"expectedVersion\":"+d.path("version").asLong()+"}");
        assertEquals(400,result.path("code").asInt());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ledger_expense",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ledger_budget",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ledger_chat_expense_link",Integer.class));
    }
    @Test void foreignUserCannotConfirmDraft() throws Exception {
        var d=createDraft(); token=jwt.generateLedgerToken(202,"test202");
        var result=call("POST","/api/ledger/chat/drafts/"+d.path("id").asLong()+"/confirm","{\"expectedVersion\":1}");
        assertEquals(404,result.path("code").asInt());
    }
    @Test void revokeRejectsRecordsEditedAfterConfirmation() throws Exception {
        var d=createDraft();String root="/api/ledger/chat/drafts/"+d.path("id").asLong();
        var confirmed=call("POST",root+"/confirm","{\"expectedVersion\":"+d.path("version").asLong()+"}").path("data");
        jdbc.update("UPDATE ledger_expense SET record_version=record_version+1 WHERE id=?",confirmed.path("savedExpenses").get(0).path("id").asLong());
        var rejected=call("POST",root+"/revoke","{\"expectedVersion\":"+confirmed.path("version").asLong()+"}");
        assertEquals(409,rejected.path("code").asInt());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM ledger_expense WHERE is_deleted=0",Integer.class));
    }
    @Test void concurrentConfirmOnlyWritesOneBatch() throws Exception {
        var d=createDraft();String path="/api/ledger/chat/drafts/"+d.path("id").asLong()+"/confirm";
        String body="{\"expectedVersion\":"+d.path("version").asLong()+"}";
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first=executor.submit(()->call("POST",path,body));
            var second=executor.submit(()->call("POST",path,body));
            assertEquals("CONFIRMED",first.get().path("data").path("status").asText());
            assertEquals("CONFIRMED",second.get().path("data").path("status").asText());
        }
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM ledger_expense",Integer.class));
    }
    @Test void missingAmountBlocksConfirmationAndCanBeEdited() throws Exception {
        when(model.parse(any())).thenReturn(json.readTree("{\"action\":\"APPEND\",\"reply\":\"金额多少？\",\"items\":[{\"amount\":null,\"categoryId\":1001,\"description\":\"午饭\",\"date\":\"today\"}]}"));
        var d=call("POST","/api/ledger/chat/messages","{\"requestId\":\"missing\",\"text\":\"午饭忘记金额了\",\"attachmentIds\":[]}").path("data").path("draft");
        assertEquals("NEEDS_INPUT",d.path("status").asText());
        String root="/api/ledger/chat/drafts/"+d.path("id").asLong();
        var blocked=call("POST",root+"/confirm","{\"expectedVersion\":"+d.path("version").asLong()+"}");
        assertEquals("NEEDS_INPUT",blocked.path("data").path("status").asText());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ledger_expense",Integer.class));
        var current=blocked.path("data");
        var edited=call("PUT",root+"/items/"+d.path("items").get(0).path("id").asLong(),"{\"expectedVersion\":"+current.path("version").asLong()+",\"amount\":\"30\",\"categoryId\":1001,\"description\":\"午饭\",\"recordedAt\":1791259200,\"datePrecision\":\"DATE\"}");
        assertEquals("READY",edited.path("data").path("status").asText());
    }
    @Test void duplicateMessageAndNaturalCorrectionPreserveOneDraft() throws Exception {
        var d=createDraft();
        call("POST","/api/ledger/chat/messages","{\"requestId\":\"request-1\",\"text\":\"午饭32.5，打车18\",\"attachmentIds\":[],\"expectedDraftVersion\":null}");
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM ledger_chat_message",Integer.class));
        long iid=d.path("items").get(1).path("id").asLong();
        when(model.parse(any())).thenReturn(json.readTree("{\"action\":\"EDIT\",\"reply\":\"已改为20\",\"edits\":[{\"itemId\":"+iid+",\"amount\":\"20\"}]}"));
        var modified=call("POST","/api/ledger/chat/messages","{\"requestId\":\"edit-2\",\"text\":\"第二笔改成20\",\"attachmentIds\":[],\"expectedDraftVersion\":"+d.path("version").asLong()+"}");
        assertEquals(d.path("id"),modified.path("data").path("draft").path("id"));
        assertEquals("52.50",modified.path("data").path("draft").path("total").asText());
    }
    @Test void revokeIsIdempotentAndRemovesOnlyItsBatch() throws Exception {
        var d=createDraft();String root="/api/ledger/chat/drafts/"+d.path("id").asLong();
        var confirmed=call("POST",root+"/confirm","{\"expectedVersion\":"+d.path("version").asLong()+"}").path("data");
        String body="{\"expectedVersion\":"+confirmed.path("version").asLong()+"}";
        assertEquals("REVOKED",call("POST",root+"/revoke",body).path("data").path("status").asText());
        assertEquals("REVOKED",call("POST",root+"/revoke",body).path("data").path("status").asText());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ledger_expense WHERE is_deleted=0",Integer.class));
    }
    @Test void uploadedImageIsPrivate() throws Exception {
        var image=new java.awt.image.BufferedImage(10,10,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
        var file=new org.springframework.mock.web.MockMultipartFile("file","receipt.png","image/png",bytes.toByteArray());
        var response=mvc.perform(multipart("/api/ledger/chat/attachments").file(file).header("Authorization","Bearer "+token)).andReturn().getResponse();
        var attachment=json.readTree(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
        String path="/api/ledger/chat/attachments/"+attachment.path("id").asLong()+"/content";
        mvc.perform(get(path).header("Authorization","Bearer "+token)).andExpect(content().contentType("image/png"));
        token=jwt.generateLedgerToken(202,"test202");
        assertEquals(404,call("GET",path,null).path("code").asInt());
    }
}
