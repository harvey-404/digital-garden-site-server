package com.harvey.digitalgarden.ledger.chat;

import static com.harvey.digitalgarden.ledger.chat.ChatStore.number;
import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.ledger.chat.ChatDtos.*;
import com.harvey.digitalgarden.dto.ledger.LedgerExpenseRequest;
import com.harvey.digitalgarden.service.ledger.LedgerExpenseService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ChatConfirmService {
    private final ChatStore store;
    private final ChatDraftService drafts;
    private final LedgerExpenseService expenses;
    private final EntityManager em;
    public ChatConfirmService(ChatStore store,ChatDraftService drafts,LedgerExpenseService expenses,EntityManager em) {
        this.store=store;this.drafts=drafts;this.expenses=expenses;this.em=em;
    }
    public Draft confirm(long uid,long did,long version) {
        return store.tx(()->{
            var session=store.lock(uid);store.checkIdle(session);var row=store.ownedDraft(uid,did);
            if("CONFIRMED".equals(row.get("status")))return store.draft(uid,did);
            store.requireEditable(row);store.requireVersion(row,version);
            store.db.queryForList("SELECT id FROM ledger_category WHERE user_id=? AND is_deleted=0 ORDER BY id FOR UPDATE",uid);
            List<Item> items=store.items(uid,did);
            drafts.validate(uid,did,items);
            if(items.isEmpty()) throw BusinessException.badRequest("至少保留一笔消费");
            if(items.stream().anyMatch(i->!i.getIssues().isEmpty())) { drafts.bump(uid,did);return store.draft(uid,did); }
            for(Item item:items) {
                LedgerExpenseRequest req=new LedgerExpenseRequest();
                req.setCategoryId(item.getCategoryId());req.setAmount(new BigDecimal(item.getAmount()));req.setDescription(item.getDescription());req.setRecordedAt(item.getRecordedAt());
                var expense=expenses.createExpense(uid,req);em.flush();
                long rv=store.db.queryForObject("SELECT record_version FROM ledger_expense WHERE id=?",Long.class,expense.getId());
                SavedExpense snapshot=new SavedExpense(expense.getId(),expense.getAmount().setScale(2).toPlainString(),expense.getCategoryId(),expense.getDescription(),expense.getRecordedAt());
                store.insert("INSERT INTO ledger_chat_expense_link(user_id,draft_id,draft_item_id,expense_id,expense_version,snapshot_json,source_attachment_ids_json) VALUES(?,?,?,?,?,?,?)",uid,did,item.getId(),expense.getId(),rv,store.write(snapshot),store.write(item.getSourceAttachmentIds()));
            }
            store.db.update("UPDATE ledger_chat_draft SET status='CONFIRMED',version=version+1 WHERE user_id=? AND id=?",uid,did);
            store.db.update("UPDATE ledger_chat_session SET active_draft_id=NULL WHERE user_id=? AND active_draft_id=?",uid,did);
            return store.draft(uid,did);
        });
    }
    public Draft revoke(long uid,long did,long version) {
        return store.tx(()->{
            var session=store.lock(uid);store.checkIdle(session);var row=store.ownedDraft(uid,did);
            if("REVOKED".equals(row.get("status")))return store.draft(uid,did);
            if(!"CONFIRMED".equals(row.get("status")))throw BusinessException.conflict("只有已记账记录可以撤销");
            store.requireVersion(row,version);
            var links=store.db.queryForList("SELECT * FROM ledger_chat_expense_link WHERE user_id=? AND draft_id=? ORDER BY expense_id",uid,did);
            for(var link:links) {
                var current=store.db.queryForList("SELECT * FROM ledger_expense WHERE user_id=? AND id=? FOR UPDATE",uid,link.get("expense_id"));
                if(current.isEmpty() || number(current.getFirst(),"is_deleted")!=0 || number(current.getFirst(),"record_version")!=number(link,"expense_version"))
                    throw BusinessException.conflict("本次账目已被修改或删除，请在明细页逐笔处理");
                SavedExpense before=store.read((String)link.get("snapshot_json"),SavedExpense.class);
                var c=current.getFirst();
                if(new BigDecimal(before.amount()).compareTo((BigDecimal)c.get("amount"))!=0 || before.categoryId()!=number(c,"category_id") || before.recordedAt()!=number(c,"recorded_at") || !before.description().equals(c.get("description")))
                    throw BusinessException.conflict("本次账目已被修改，请在明细页逐笔处理");
            }
            for(var link:links)store.db.update("UPDATE ledger_expense SET is_deleted=1,record_version=record_version+1,update_dtm=UNIX_TIMESTAMP() WHERE user_id=? AND id=?",uid,link.get("expense_id"));
            store.db.update("UPDATE ledger_chat_draft SET status='REVOKED',version=version+1 WHERE user_id=? AND id=?",uid,did);
            return store.draft(uid,did);
        });
    }
}
