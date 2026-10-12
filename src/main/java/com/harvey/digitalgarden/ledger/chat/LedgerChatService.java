package com.harvey.digitalgarden.ledger.chat;

import static com.harvey.digitalgarden.ledger.chat.ChatStore.number;
import com.fasterxml.jackson.databind.JsonNode;
import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.ledger.chat.ChatDtos.*;
import com.harvey.digitalgarden.ledger.chat.ai.LedgerChatModel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class LedgerChatService {
    private final ChatStore store;
    private final ChatDraftService drafts;
    private final LedgerChatModel model;
    private final LedgerChatAttachmentService attachments;
    private final LedgerGuestService guests;
    record Lease(long uid,long messageId,String requestId,String attempt,long receivedAt,Long draftId,long version,Turn cached) {}
    public LedgerChatService(ChatStore store,ChatDraftService drafts,LedgerChatModel model,LedgerChatAttachmentService attachments,LedgerGuestService guests) {
        this.store=store;this.drafts=drafts;this.model=model;this.attachments=attachments;
        this.guests=guests;
    }
    public History history(long uid,Long beforeId,int limit) {
        return store.tx(()->{
            var session=store.lock(uid);
            if(number(session,"processing_until")<=Instant.now().getEpochSecond())store.checkIdle(session);
            int size=Math.max(1,Math.min(100,limit));
            List<Map<String,Object>> rows=store.db.queryForList("SELECT * FROM ledger_chat_message WHERE user_id=? AND id<? ORDER BY id DESC LIMIT ?",uid,beforeId==null?Long.MAX_VALUE:beforeId,size+1);
            boolean more=rows.size()>size;if(more) rows=new ArrayList<>(rows.subList(0,size));
            Collections.reverse(rows);
            List<Message> messages=rows.stream().map(store::message).toList();
            Set<Long> ids=new LinkedHashSet<>();messages.forEach(m->{if(m.draftId()!=null)ids.add(m.draftId());});
            long aid=number(session,"active_draft_id");if(aid!=0) ids.add(aid);
            var persisted=ids.stream().map(id->store.draft(uid,id)).toList();
            return new History(messages,persisted,aid==0?null:store.draft(uid,aid),more?messages.getFirst().id():null,
                number(session,"processing_until")>Instant.now().getEpochSecond()?(String)session.get("processing_request_id"):null,new Capabilities(model.available(),model.available()),guests.view(uid));
        });
    }
    public RequestState requestState(long uid,String requestId) {
        return store.tx(()->{
            var session=store.lock(uid);
            if(number(session,"processing_until")<=Instant.now().getEpochSecond())store.checkIdle(session);
            var rows=store.db.queryForList("SELECT * FROM ledger_chat_message WHERE user_id=? AND request_id=? AND role='user'",uid,requestId);
            if(rows.isEmpty())throw BusinessException.notFound("消息不存在");
            var row=rows.getFirst();String status=(String)row.get("status");
            return new RequestState(status,"DONE".equals(status)?cachedTurn(uid,row):null);
        });
    }
    private Turn cachedTurn(long uid,Map<String,Object> row) {
        Turn turn=store.read((String)row.get("response_json"),Turn.class);
        var assistant=store.db.queryForMap("SELECT * FROM ledger_chat_message WHERE user_id=? AND request_id=? AND role='assistant'",uid,row.get("request_id"));
        return new Turn(store.message(row),store.message(assistant),turn.draft()==null?null:store.draft(uid,turn.draft().id()));
    }
    public Turn send(long uid,Send req) {
        if(req==null || req.requestId()==null || !req.requestId().matches("[a-zA-Z0-9_-]{1,64}"))throw BusinessException.badRequest("消息编号无效");
        String text=req.text()==null?"":req.text().trim();
        List<Long> imageIds=req.attachmentIds()==null?List.of():req.attachmentIds();
        if(text.length()>4000 || imageIds.size()>3 || new HashSet<>(imageIds).size()!=imageIds.size() || imageIds.stream().anyMatch(Objects::isNull) || (text.isBlank() && imageIds.isEmpty()))
            throw BusinessException.badRequest("请输入文字或图片，最多4000字和3张图片");
        String payloadHash=sha256(store.write(List.of(text,imageIds,req.expectedDraftVersion()==null?0:req.expectedDraftVersion())).getBytes(StandardCharsets.UTF_8));
        Lease lease=store.tx(()->{
            var session=store.lock(uid);
            var old=store.db.queryForList("SELECT * FROM ledger_chat_message WHERE user_id=? AND request_id=? AND role='user'",uid,req.requestId());
            if(!old.isEmpty()) {
                var row=old.getFirst();
                if(!payloadHash.equals(row.get("payload_hash")))throw BusinessException.conflict("重试内容已改变，请发送新消息");
                if("DONE".equals(row.get("status")))return new Lease(uid,number(row,"id"),req.requestId(),"",number(row,"received_at"),null,0,cachedTurn(uid,row));
            }
            store.checkIdle(session);
            guests.reserve(uid);
            if(!model.available())throw BusinessException.badRequest("记账助手尚未配置，请先使用手动记账");
            long did=number(session,"active_draft_id");long version=did==0?0:number(store.ownedDraft(uid,did),"version");
            if((req.expectedDraftVersion()==null?0:req.expectedDraftVersion())!=version)throw BusinessException.conflict("内容已更新，请重新核对");
            for(Long id:imageIds)attachments.loadOwned(uid,id);
            long now=Instant.now().getEpochSecond();long received=old.isEmpty()?now:number(old.getFirst(),"received_at");
            long mid=old.isEmpty()?store.insert("INSERT INTO ledger_chat_message(user_id,session_id,role,request_id,text,status,attachment_ids_json,payload_hash,received_at) VALUES(?,?,'user',?,?,'PROCESSING',?,?,?)",uid,session.get("id"),req.requestId(),text,store.write(imageIds),payloadHash,received):number(old.getFirst(),"id");
            store.db.update("UPDATE ledger_chat_message SET status='PROCESSING' WHERE id=? AND user_id=?",mid,uid);
            String attempt=UUID.randomUUID().toString();
            store.db.update("UPDATE ledger_chat_session SET processing_request_id=?,processing_attempt=?,processing_until=? WHERE user_id=?",req.requestId(),attempt,now+90,uid);
            return new Lease(uid,mid,req.requestId(),attempt,received,did==0?null:did,version,null);
        });
        if(lease.cached()!=null)return lease.cached();
        try {
            JsonNode output;
            if(text.matches("^(确认|记上吧|记账|确认记账|保存|确认全部记账)[。！!]?$") && imageIds.isEmpty())
                output=store.json.createObjectNode().put("action","CLARIFY").put("reply","请核对下方列表，点击「确认全部记账」后保存。");
            else {
                var historyRows=store.db.queryForList("SELECT * FROM ledger_chat_message WHERE user_id=? AND id<? AND status='DONE' ORDER BY id DESC LIMIT 20",uid,lease.messageId());
                Collections.reverse(historyRows);
                List<Message> history=historyRows.stream().map(store::message).toList();
                output=model.parse(new LedgerChatModel.Input(text,lease.receivedAt(),imageIds.stream().map(id->attachments.loadOwned(uid,id)).toList(),imageIds,
                    store.db.queryForList("SELECT id,name FROM ledger_category WHERE user_id=? AND is_deleted=0",uid),lease.draftId()==null?null:store.draft(uid,lease.draftId()),history));
            }
            return finish(lease,output);
        } catch(RuntimeException ex) {
            store.tx(()->{
                var session=store.lock(uid);
                if(lease.attempt().equals(session.get("processing_attempt"))) {
                    store.db.update("UPDATE ledger_chat_message SET status='FAILED' WHERE user_id=? AND id=?",uid,lease.messageId());
                    clear(uid);
                }
                return null;
            });
            if(ex instanceof BusinessException b)throw b;
            throw BusinessException.badRequest("识别未完成，原消息与草稿已保留，请重试");
        }
    }
    private Turn finish(Lease lease,JsonNode output) {
        return store.tx(()->{
            long uid=lease.uid();var session=store.lock(uid);
            if(!lease.attempt().equals(session.get("processing_attempt")) || number(session,"processing_until")<Instant.now().getEpochSecond())throw BusinessException.conflict("识别已超时，请重试");
            long active=number(session,"active_draft_id");
            if(active!=(lease.draftId()==null?0:lease.draftId()) || (active!=0 && number(store.ownedDraft(uid,active),"version")!=lease.version()))throw BusinessException.conflict("草稿已更新，请重新核对");
            var userRow=store.db.queryForMap("SELECT * FROM ledger_chat_message WHERE user_id=? AND id=?",uid,lease.messageId());
            List<Long> sourceIds=store.ids((String)userRow.get("attachment_ids_json"));
            if(output==null || !output.isObject())throw BusinessException.badRequest("识别结果不完整，请重试");
            String action=output.path("action").asText();
            if(!Set.of("APPEND","EDIT","CLARIFY").contains(action))throw BusinessException.badRequest("识别结果不完整，请重试");
            if("CLARIFY".equals(action) && (!output.path("reply").isTextual() || output.path("reply").asText().isBlank()))throw BusinessException.badRequest("识别结果不完整，请重试");
            if("APPEND".equals(action) && (!output.path("items").isArray() || output.path("items").isEmpty()))throw BusinessException.badRequest("识别结果不完整，请重试");
            Long did=lease.draftId();
            if("APPEND".equals(action) && output.path("items").isArray() && !output.path("items").isEmpty()) {
                int count=(did==null?0:store.items(uid,did).size())+output.path("items").size();
                if(count>50)throw BusinessException.badRequest("每批最多50笔，请拆分后发送");
                if(did==null) {
                    did=store.insert("INSERT INTO ledger_chat_draft(user_id,session_id,status) VALUES(?,?,'NEEDS_INPUT')",uid,session.get("id"));
                    store.db.update("UPDATE ledger_chat_session SET active_draft_id=? WHERE user_id=?",did,uid);
                }
                List<Item> items=store.items(uid,did);
                for(JsonNode node:output.path("items"))items.add(candidate(node,lease.messageId(),sourceIds,lease.receivedAt()));
                drafts.validate(uid,did,items);drafts.bump(uid,did);
            } else if("EDIT".equals(action)) {
                if(did==null || !output.path("edits").isArray() || output.path("edits").isEmpty())throw BusinessException.badRequest("没有可修改的草稿，请明确消费信息");
                List<Item> items=store.items(uid,did);
                for(JsonNode node:output.path("edits")) {
                    long iid=node.path("itemId").asLong();
                    Item item=items.stream().filter(i->i.getId()==iid).findFirst().orElseThrow(()->BusinessException.badRequest("无法定位这笔消费，请明确序号"));
                    if(node.path("remove").asBoolean()) { store.db.update("UPDATE ledger_chat_draft_item SET is_removed=1 WHERE id=? AND user_id=?",iid,uid);items.remove(item);continue; }
                    if(node.has("amount"))item.setAmount(nullableText(node,"amount"));
                    if(node.has("categoryId"))item.setCategoryId(node.path("categoryId").isIntegralNumber()?node.path("categoryId").asLong():null);
                    if(node.has("description"))item.setDescription(node.path("description").asText(""));
                    if(node.has("date") || node.has("dateConflict"))setDate(item,node,lease.receivedAt());
                    item.setDuplicateAcknowledged(false);
                }
                drafts.validate(uid,did,items);drafts.bump(uid,did);
            }
            String reply=output.path("reply").asText("请核对消费信息。");
            if("APPEND".equals(action)) reply="已整理消费信息，请核对下方列表，确认后才会入账。";
            if("EDIT".equals(action)) reply="已更新待确认列表，请核对后确认记账。";
            if(reply.length()>2000)reply=reply.substring(0,2000);
            store.db.update("UPDATE ledger_chat_message SET status='DONE',draft_id=? WHERE user_id=? AND id=?",did,uid,lease.messageId());
            long assistant=store.insert("INSERT INTO ledger_chat_message(user_id,session_id,role,request_id,text,status,draft_id,attachment_ids_json,received_at) VALUES(?,?,'assistant',?,?,'DONE',?,'[]',?)",uid,session.get("id"),lease.requestId(),reply,did,Instant.now().getEpochSecond());
            Turn turn=new Turn(store.message(store.db.queryForMap("SELECT * FROM ledger_chat_message WHERE id=?",lease.messageId())),store.message(store.db.queryForMap("SELECT * FROM ledger_chat_message WHERE id=?",assistant)),did==null?null:store.draft(uid,did));
            store.db.update("UPDATE ledger_chat_message SET response_json=? WHERE id=? AND user_id=?",store.write(turn),lease.messageId(),uid);
            guests.confirm(uid);clear(uid);return turn;
        });
    }
    private void clear(long uid) { store.db.update("UPDATE ledger_chat_session SET processing_request_id=NULL,processing_attempt=NULL,processing_until=0 WHERE user_id=?",uid); }
    private Item candidate(JsonNode node,long messageId,List<Long> sources,long receivedAt) {
        Item item=new Item();item.setAmount(nullableText(node,"amount"));
        item.setCategoryId(node.path("categoryId").isIntegralNumber()?node.path("categoryId").asLong():null);
        item.setDescription(node.path("description").asText(""));item.setSourceMessageId(messageId);
        List<Long> declared=new ArrayList<>();
        if(node.path("sourceAttachmentIds").isArray())for(JsonNode id:node.path("sourceAttachmentIds")) { if(!sources.contains(id.asLong()))throw BusinessException.badRequest("图片来源不匹配，请重试");declared.add(id.asLong()); }
        item.setSourceAttachmentIds(declared.isEmpty()?new ArrayList<>(sources):declared);
        item.setEvidence(node.path("evidence").asText(""));item.setOrderGroupKey(nullableText(node,"orderGroupKey"));item.setOrderPaidTotal(nullableText(node,"orderPaidTotal"));
        setDate(item,node,receivedAt);return item;
    }
    private void setDate(Item item,JsonNode node,long receivedAt) {
        item.setDefaultedToday(false);item.setRecordedAt(null);item.setDatePrecision("DATE");
        if(node.path("dateConflict").asBoolean())return;
        try {
            LocalDate today=Instant.ofEpochSecond(receivedAt).atZone(ChatDraftValidator.ZONE).toLocalDate();
            String raw=nullableText(node,"date");LocalDate date;
            if(raw==null || raw.isBlank()) {date=today;item.setDefaultedToday(true);}
            else date=switch(raw) {case "today","今天"->today;case "yesterday","昨天"->today.minusDays(1);case "前天"->today.minusDays(2);default->LocalDate.parse(raw);};
            String time=nullableText(node,"time");
            item.setRecordedAt(time==null?ChatDraftValidator.dateAtNoon(date):date.atTime(LocalTime.parse(time)).atZone(ChatDraftValidator.ZONE).toEpochSecond());
            item.setDatePrecision(time==null?"DATE":"DATETIME");
        } catch(RuntimeException ex) {item.setRecordedAt(null);}
    }
    private static String nullableText(JsonNode node,String key) { return node.hasNonNull(key)?node.get(key).asText():null; }
    public static String sha256(byte[] value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
        catch(Exception ex) {throw new IllegalStateException(ex);}
    }
}
