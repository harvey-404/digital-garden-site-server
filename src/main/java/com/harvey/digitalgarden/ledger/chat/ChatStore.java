package com.harvey.digitalgarden.ledger.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.ledger.chat.ChatDtos.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** All chat mutations use the existing datasource transaction manager. */
@Component
public class ChatStore {
    final JdbcTemplate db;
    final ObjectMapper json;
    private final TransactionTemplate transaction;
    public ChatStore(JdbcTemplate db, ObjectMapper json, PlatformTransactionManager manager) {
        this.db=db; this.json=json; this.transaction=new TransactionTemplate(manager);
    }
    public <T> T tx(Supplier<T> work) { return transaction.execute(status -> work.get()); }
    public long insert(String sql, Object... args) {
        var key=new GeneratedKeyHolder();
        db.update(connection -> {
            PreparedStatement statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for (int i=0;i<args.length;i++) statement.setObject(i+1,args[i]);
            return statement;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }
    public String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("账本数据编码失败",ex); }
    }
    public <T> T read(String value,Class<T> type) {
        try { return json.readValue(value,type); }
        catch (Exception ex) { throw new IllegalStateException("账本数据读取失败",ex); }
    }
    public List<Long> ids(String value) {
        try { return json.readValue(value,new TypeReference<List<Long>>(){}); }
        catch(Exception ex) { throw new IllegalStateException("账本来源读取失败",ex); }
    }
    public Map<String,Object> lock(long uid) {
        var attributes=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if(attributes instanceof org.springframework.web.context.request.ServletRequestAttributes request && request.getRequest().getAttribute(com.harvey.digitalgarden.account.AccountPolicy.ATTRIBUTE) instanceof com.harvey.digitalgarden.account.AccountService.Account account) {
            db.queryForObject("SELECT id FROM site_account WHERE id=? FOR UPDATE",Long.class,account.id());
            var mapping=db.queryForList("SELECT local_user_id FROM site_account_link WHERE account_id=? AND module='ledger'",Long.class,account.id());
            if(mapping.isEmpty() || mapping.getFirst()!=uid)throw BusinessException.conflict("账本身份已更新，请刷新后重试");
        }
        return lockForMigration(uid);
    }
    Map<String,Object> lockForMigration(long uid) {
        var users=db.queryForList("SELECT id FROM ledger_user WHERE id=? AND is_deleted=0 AND status='active' FOR UPDATE",uid);
        if (users.isEmpty()) throw BusinessException.forbidden("ACCOUNT_DISABLED");
        db.update("INSERT IGNORE INTO ledger_chat_session(user_id) VALUES(?)",uid);
        return db.queryForMap("SELECT * FROM ledger_chat_session WHERE user_id=? FOR UPDATE",uid);
    }
    public static long number(Map<String,Object> row,String key) {
        Object n=row.get(key); return n==null?0:n instanceof Boolean b?(b?1:0):((Number)n).longValue();
    }
    public void checkIdle(Map<String,Object> session) {
        long until=number(session,"processing_until");
        if (until > java.time.Instant.now().getEpochSecond()) throw BusinessException.conflict("正在识别，请稍后再操作");
        if (session.get("processing_request_id")!=null) {
            db.update("UPDATE ledger_chat_message SET status='FAILED' WHERE user_id=? AND request_id=? AND role='user' AND status='PROCESSING'",
                session.get("user_id"),session.get("processing_request_id"));
            db.update("UPDATE ledger_chat_session SET processing_request_id=NULL,processing_attempt=NULL,processing_until=0 WHERE id=?",session.get("id"));
        }
    }
    public Map<String,Object> ownedDraft(long uid,long id) {
        var rows=db.queryForList("SELECT * FROM ledger_chat_draft WHERE user_id=? AND id=?",uid,id);
        if(rows.isEmpty()) throw BusinessException.notFound("草稿不存在");
        return rows.getFirst();
    }
    public List<Item> items(long uid,long draftId) {
        return db.query("SELECT id,ordinal,data_json FROM ledger_chat_draft_item WHERE user_id=? AND draft_id=? AND is_removed=0 ORDER BY ordinal,id",
            (rs,n)->{ Item item=read(rs.getString("data_json"),Item.class); item.setId(rs.getLong("id")); item.setOrdinal(rs.getInt("ordinal")); return item; },uid,draftId);
    }
    public void putItem(long uid,long draftId,Item item) {
        if(item.getId()==0) item.setId(insert("INSERT INTO ledger_chat_draft_item(user_id,draft_id,ordinal,data_json) VALUES(?,?,?,?)",uid,draftId,item.getOrdinal(),write(item)));
        db.update("UPDATE ledger_chat_draft_item SET ordinal=?,data_json=? WHERE id=? AND user_id=? AND draft_id=?",
            item.getOrdinal(),write(item),item.getId(),uid,draftId);
    }
    public Draft draft(long uid,long id) {
        Map<String,Object> row=ownedDraft(uid,id);
        List<Item> items=items(uid,id);
        BigDecimal total=BigDecimal.ZERO;
        for(Item item:items) if(ChatDraftValidator.amountIssue(item.getAmount())==null) total=total.add(new BigDecimal(item.getAmount()));
        String status=(String)row.get("status");
        List<SavedExpense> saved=db.query("SELECT snapshot_json FROM ledger_chat_expense_link WHERE user_id=? AND draft_id=? ORDER BY id",
            (rs,n)->read(rs.getString(1),SavedExpense.class),uid,id);
        return new Draft(id,number(row,"version"),status,items,total.setScale(2).toPlainString(),items.size(),
            "READY".equals(status) && !items.isEmpty() && items.stream().allMatch(i->i.getIssues().isEmpty()),saved);
    }
    public Message message(Map<String,Object> row) {
        Long draft=row.get("draft_id")==null?null:number(row,"draft_id");
        return new Message(number(row,"id"),(String)row.get("role"),(String)row.get("text"),ids((String)row.get("attachment_ids_json")),
            number(row,"received_at"),(String)row.get("request_id"),(String)row.get("status"),draft);
    }
    public List<Long> categoryIds(long uid) {
        return db.queryForList("SELECT id FROM ledger_category WHERE user_id=? AND is_deleted=0 ORDER BY id",Long.class,uid);
    }
    public void requireVersion(Map<String,Object> row,long version) {
        if(number(row,"version")!=version) throw BusinessException.conflict("内容已更新，请重新核对");
    }
    public void requireEditable(Map<String,Object> row) {
        if(!Set.of("READY","NEEDS_INPUT").contains(row.get("status"))) throw BusinessException.conflict("这份草稿已结束，请重新记一笔");
    }
}
