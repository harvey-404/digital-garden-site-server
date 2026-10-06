package com.harvey.digitalgarden.ledger.chat;

import static com.harvey.digitalgarden.ledger.chat.ChatStore.number;
import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.ledger.chat.ChatDtos.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ChatDraftService {
    final ChatStore store;
    public ChatDraftService(ChatStore store) { this.store=store; }
    public void validate(long uid,long draftId,List<Item> items) {
        List<Long> categories=store.categoryIds(uid);
        int ordinal=0;
        for(Item item:items) {
            item.setOrdinal(++ordinal);
            List<Long> duplicates=duplicates(uid,item);
            if(!duplicates.equals(item.getDuplicateExpenseIds())) item.setDuplicateAcknowledged(false);
            item.setDuplicateExpenseIds(duplicates);
            List<SavedExpense> records=new ArrayList<>();
            for(Long duplicate:duplicates)for(var row:store.db.queryForList("SELECT * FROM ledger_expense WHERE user_id=? AND id=? AND is_deleted=0",uid,duplicate))
                records.add(new SavedExpense(duplicate,((BigDecimal)row.get("amount")).setScale(2).toPlainString(),number(row,"category_id"),(String)row.get("description"),number(row,"recorded_at")));
            item.setDuplicateRecords(records);
            ChatDraftValidator.validate(item,categories);
        }
        Map<String,List<Item>> groups=new HashMap<>();
        for(Item item:items) if(item.getOrderGroupKey()!=null && !item.getOrderGroupKey().isBlank())
            groups.computeIfAbsent(item.getSourceMessageId()+":"+item.getOrderGroupKey(),k->new ArrayList<>()).add(item);
        for(List<Item> group:groups.values()) {
            String paid=group.getFirst().getOrderPaidTotal();
            if(paid==null) continue;
            boolean invalid=ChatDraftValidator.amountIssue(paid)!=null || group.stream().anyMatch(i->ChatDraftValidator.amountIssue(i.getAmount())!=null);
            BigDecimal total=group.stream().filter(i->ChatDraftValidator.amountIssue(i.getAmount())==null).map(i->new BigDecimal(i.getAmount())).reduce(BigDecimal.ZERO,BigDecimal::add);
            if(invalid || total.compareTo(new BigDecimal(paid))!=0)
                group.forEach(i->i.getIssues().add(new Issue("SPLIT_TOTAL_MISMATCH","拆分合计与实际支付不一致，请修改或移除")));
        }
        for(Item item:items) store.putItem(uid,draftId,item);
        String status=!items.isEmpty() && items.stream().allMatch(i->i.getIssues().isEmpty())?"READY":"NEEDS_INPUT";
        store.db.update("UPDATE ledger_chat_draft SET status=? WHERE user_id=? AND id=?",status,uid,draftId);
    }
    private List<Long> duplicates(long uid,Item item) {
        Set<Long> matches=new TreeSet<>();
        if(ChatDraftValidator.amountIssue(item.getAmount())==null && item.getRecordedAt()!=null && item.getRecordedAt()>0) {
            LocalDate date=Instant.ofEpochSecond(item.getRecordedAt()).atZone(ChatDraftValidator.ZONE).toLocalDate();
            long start=date.atStartOfDay(ChatDraftValidator.ZONE).toEpochSecond();
            for(var row:store.db.queryForList("SELECT id,description FROM ledger_expense WHERE user_id=? AND is_deleted=0 AND amount=? AND recorded_at>=? AND recorded_at<?",uid,new BigDecimal(item.getAmount()),start,date.plusDays(1).atStartOfDay(ChatDraftValidator.ZONE).toEpochSecond()))
                if(normalize((String)row.get("description")).equals(normalize(item.getDescription()))) matches.add(number(row,"id"));
        }
        for(Long aid:item.getSourceAttachmentIds()) {
            var hashes=store.db.queryForList("SELECT hash FROM ledger_chat_attachment WHERE user_id=? AND id=?",String.class,uid,aid);
            if(hashes.isEmpty()) throw BusinessException.notFound("图片不存在");
            var links=store.db.queryForList("SELECT l.expense_id,l.source_attachment_ids_json FROM ledger_chat_expense_link l JOIN ledger_expense e ON e.id=l.expense_id WHERE l.user_id=? AND e.is_deleted=0",uid);
            for(var link:links) for(Long oldId:store.ids((String)link.get("source_attachment_ids_json"))) {
                var old=store.db.queryForList("SELECT hash FROM ledger_chat_attachment WHERE user_id=? AND id=?",String.class,uid,oldId);
                if(!old.isEmpty() && old.getFirst().equals(hashes.getFirst())) matches.add(number(link,"expense_id"));
            }
        }
        return new ArrayList<>(matches);
    }
    private static String normalize(String text) { return text==null?"":text.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Punct}，。！？]",""); }
    public Draft edit(long uid,long did,long iid,Edit edit) {
        return store.tx(()->{
            var session=store.lock(uid); store.checkIdle(session);
            var row=store.ownedDraft(uid,did); store.requireEditable(row); store.requireVersion(row,edit.expectedVersion());
            List<Item> items=store.items(uid,did);
            Item item=items.stream().filter(i->i.getId()==iid).findFirst().orElseThrow(()->BusinessException.notFound("条目不存在"));
            boolean fieldsChanged=!Objects.equals(item.getAmount(),edit.amount()) || !Objects.equals(item.getCategoryId(),edit.categoryId()) || !Objects.equals(item.getDescription(),edit.description()) || !Objects.equals(item.getRecordedAt(),edit.recordedAt());
            item.setAmount(edit.amount());item.setCategoryId(edit.categoryId());item.setDescription(edit.description()==null?"":edit.description().trim());
            item.setRecordedAt(edit.recordedAt()); item.setDatePrecision("DATETIME".equals(edit.datePrecision())?"DATETIME":"DATE");
            item.setDefaultedToday(false); item.setDuplicateAcknowledged(!fieldsChanged && edit.duplicateAcknowledged());
            validate(uid,did,items); bump(uid,did); return store.draft(uid,did);
        });
    }
    public Draft remove(long uid,long did,long iid,long version) {
        return store.tx(()->{
            var session=store.lock(uid);store.checkIdle(session);
            var row=store.ownedDraft(uid,did);store.requireEditable(row);store.requireVersion(row,version);
            int changed=store.db.update("UPDATE ledger_chat_draft_item SET is_removed=1 WHERE user_id=? AND draft_id=? AND id=? AND is_removed=0",uid,did,iid);
            if(changed==0) throw BusinessException.notFound("条目不存在");
            validate(uid,did,store.items(uid,did));bump(uid,did);return store.draft(uid,did);
        });
    }
    public Draft cancel(long uid,long did,long version) {
        return store.tx(()->{
            var session=store.lock(uid);store.checkIdle(session);var row=store.ownedDraft(uid,did);
            if("CANCELLED".equals(row.get("status")))return store.draft(uid,did);
            store.requireEditable(row);store.requireVersion(row,version);
            store.db.update("UPDATE ledger_chat_draft SET status='CANCELLED',version=version+1 WHERE user_id=? AND id=?",uid,did);
            store.db.update("UPDATE ledger_chat_session SET active_draft_id=NULL WHERE user_id=? AND active_draft_id=?",uid,did);
            return store.draft(uid,did);
        });
    }
    void bump(long uid,long did) { store.db.update("UPDATE ledger_chat_draft SET version=version+1 WHERE user_id=? AND id=?",uid,did); }
}
