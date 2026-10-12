package com.harvey.digitalgarden.ledger.chat;

import static com.harvey.digitalgarden.ledger.chat.ChatStore.number;
import com.harvey.digitalgarden.account.*;
import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.security.LedgerPrincipal;
import com.harvey.digitalgarden.service.ledger.LedgerUserAdminService;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class LedgerGuestService {
    public static final String COOKIE="dg_guest_ledger";
    public static final String ATTRIBUTE="ledger.guest";
    private static final long TTL=180L*86400;
    private final ChatStore store;
    private final AccountRateLimit rate;
    private final ChatDraftService drafts;
    private final boolean secure;
    private final SecureRandom random=new SecureRandom();
    public record View(boolean isGuest, Integer remaining, int limit, String identityKey, String adoptedGuestKey) {}
    public record Identity(LedgerPrincipal principal,String token) {}
    public LedgerGuestService(ChatStore store,AccountRateLimit rate,ChatDraftService drafts,@Value("${app.account.secure-cookie:true}")boolean secure) {
        this.store=store;this.rate=rate;this.drafts=drafts;this.secure=secure;
    }
    public static String cookie(HttpServletRequest request) {
        String token=null;
        if(request.getCookies()!=null)for(Cookie c:request.getCookies())if(COOKIE.equals(c.getName())) {
            if(token!=null)return "invalid-duplicate-cookie";token=c.getValue();
        }
        return token;
    }
    public void setCookie(HttpServletResponse out,String token) {
        out.addHeader("Set-Cookie",ResponseCookie.from(COOKIE,token).httpOnly(true).secure(secure).sameSite("Lax").path("/").maxAge(TTL).build().toString());
    }
    public Identity resolve(String token,String ip,boolean provision) {
        if(token==null) {
            if(!provision)throw unauthorized();
            rate.consume("ledger-guest-create:"+ip,20,3600000);
            byte[] bytes=new byte[32];random.nextBytes(bytes);
            String created=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            return store.tx(()->{
                long now=Instant.now().getEpochSecond();String sn="guest_"+UUID.randomUUID().toString().replace("-","");
                // Guest invites are intentionally outside the legacy invite alphabet/shape.
                long uid=store.insert("INSERT INTO ledger_user(user_sn,invite_code,display_name,default_budget_amount,status,is_deleted,in_dtm,update_dtm) VALUES(?,?,'游客',0,'active',0,?,?)",sn,"g"+UUID.randomUUID().toString().replace("-","").substring(0,13),now,now);
                int i=0;for(String name:LedgerUserAdminService.defaultCategoryNames())store.db.update("INSERT INTO ledger_category(user_id,name,icon,sort_order,is_deleted,in_dtm,update_dtm) VALUES(?,?,'',?,0,?,?)",uid,name,i++,now,now);
                store.db.update("INSERT INTO ledger_guest_session(token_hash,user_id,completed,created_at,expires_at) VALUES(?,?,0,?,?)",hash(created),uid,now,now+TTL);
                return new Identity(new LedgerPrincipal(uid,sn),created);
            });
        }
        if(!token.matches("[A-Za-z0-9_-]{43}"))throw unauthorized();
        var rows=store.db.queryForList("SELECT g.*,u.user_sn FROM ledger_guest_session g JOIN ledger_user u ON u.id=g.user_id WHERE token_hash=? AND migrated_to_account IS NULL AND expires_at>? AND u.status='active' AND u.is_deleted=0",hash(token),Instant.now().getEpochSecond());
        if(rows.isEmpty())throw unauthorized();var row=rows.getFirst();
        return new Identity(new LedgerPrincipal(number(row,"user_id"),(String)row.get("user_sn")),null);
    }
    public View view(long uid) {
        var rows=store.db.queryForList("SELECT completed,migrated_to_account FROM ledger_guest_session WHERE user_id=?",uid);
        if(rows.isEmpty())return new View(false,null,3,"ledger:"+uid,adoptedGuestKey(uid));
        if(rows.getFirst().get("migrated_to_account")!=null)throw unauthorized();
        return new View(true,Math.max(0,3-(int)number(rows.getFirst(),"completed")),3,"guest:"+uid,null);
    }
    private String adoptedGuestKey(long uid) {
        var attributes=org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if(!(attributes instanceof org.springframework.web.context.request.ServletRequestAttributes request) || !(request.getRequest().getAttribute(AccountPolicy.ATTRIBUTE) instanceof AccountService.Account account))return null;
        String token=cookie(request.getRequest());if(token==null || !token.matches("[A-Za-z0-9_-]{43}"))return null;
        var sources=store.db.queryForList("SELECT g.user_id FROM ledger_guest_session g JOIN site_account_link l ON l.account_id=g.migrated_to_account AND l.module='ledger' WHERE g.token_hash=? AND g.migrated_to_account=? AND l.local_user_id=?",Long.class,hash(token),account.id(),uid);
        return sources.isEmpty()?null:"guest:"+sources.getFirst();
    }
    // Caller holds the chat user/session lock. Its durable 90 second lease is the reservation.
    public void reserve(long uid) {
        View state=view(uid);if(state.isGuest() && state.remaining()==0)throw new BusinessException(403,"游客三轮体验已完成，请登录或注册后继续；对话与草稿已保留");
    }
    public void confirm(long uid) {
        store.db.update("UPDATE ledger_guest_session SET completed=completed+1 WHERE user_id=? AND migrated_to_account IS NULL AND completed<3",uid);
    }
    public void migrate(String token,long account,long target) {
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}"))return;
        store.tx(()->{
            var found=store.db.queryForList("SELECT * FROM ledger_guest_session WHERE token_hash=?",hash(token));
            if(found.isEmpty())return null;
            var guest=found.getFirst();if(guest.get("migrated_to_account")!=null)return null;
            if(number(guest,"expires_at")<=Instant.now().getEpochSecond())return null;
            long source=number(guest,"user_id");
            // Lock users in a stable order, then recheck the credential under lock.
            for(long uid:new TreeSet<>(List.of(source,target)))store.db.queryForObject("SELECT id FROM ledger_user WHERE id=? FOR UPDATE",Long.class,uid);
            guest=store.db.queryForMap("SELECT * FROM ledger_guest_session WHERE token_hash=? FOR UPDATE",hash(token));
            if(guest.get("migrated_to_account")!=null)return null;
            var sourceSession=store.lockForMigration(source);var targetSession=store.lockForMigration(target);
            store.checkIdle(sourceSession);store.checkIdle(targetSession);
            Map<Long,Long> categories=new HashMap<>();
            for(var c:store.db.queryForList("SELECT * FROM ledger_category WHERE user_id=? AND is_deleted=0",source)) {
                var ids=store.db.queryForList("SELECT id FROM ledger_category WHERE user_id=? AND name=? AND is_deleted=0 ORDER BY id",Long.class,target,c.get("name"));
                long id=ids.isEmpty()?store.insert("INSERT INTO ledger_category(user_id,name,icon,sort_order,is_deleted,in_dtm,update_dtm) VALUES(?,?,?,?,0,?,?)",target,c.get("name"),c.get("icon"),c.get("sort_order"),c.get("in_dtm"),c.get("update_dtm")):ids.getFirst();
                categories.put(number(c,"id"),id);
            }
            for(String request:store.db.queryForList("SELECT request_id FROM ledger_chat_message WHERE user_id=? AND role='user'",String.class,source)) {
                if(store.db.queryForObject("SELECT COUNT(*) FROM ledger_chat_message WHERE user_id=? AND request_id=?",Integer.class,target,request)>0) {
                    String next="guest-"+source+"-"+hash(request).substring(0,24);
                    store.db.update("UPDATE ledger_chat_message SET request_id=? WHERE user_id=? AND request_id=?",next,source,request);
                }
            }
            for(var d:store.db.queryForList("SELECT id FROM ledger_chat_draft WHERE user_id=?",source))for(var item:store.items(source,number(d,"id"))) {
                if(item.getCategoryId()!=null)item.setCategoryId(categories.get(item.getCategoryId()));store.putItem(source,number(d,"id"),item);
            }
            for(String table:List.of("ledger_chat_message","ledger_chat_draft"))store.db.update("UPDATE "+table+" SET user_id=?,session_id=? WHERE user_id=?",target,targetSession.get("id"),source);
            for(String table:List.of("ledger_chat_draft_item","ledger_chat_attachment"))store.db.update("UPDATE "+table+" SET user_id=? WHERE user_id=?",target,source);
            long guestDraft=number(sourceSession,"active_draft_id");
            if(number(targetSession,"active_draft_id")==0 && guestDraft!=0)store.db.update("UPDATE ledger_chat_session SET active_draft_id=? WHERE user_id=?",guestDraft,target);
            for(var d:store.db.queryForList("SELECT id FROM ledger_chat_draft WHERE user_id=? AND session_id=? AND status IN ('READY','NEEDS_INPUT')",target,targetSession.get("id")))drafts.validate(target,number(d,"id"),store.items(target,number(d,"id")));
            store.db.update("UPDATE ledger_chat_session SET active_draft_id=NULL WHERE user_id=?",source);
            store.db.update("UPDATE ledger_guest_session SET migrated_to_account=? WHERE user_id=?",account,source);
            store.db.update("UPDATE ledger_user SET status='disabled' WHERE id=?",source);
            return null;
        });
    }
    private static String hash(String token) { return LedgerChatService.sha256(token.getBytes(StandardCharsets.UTF_8)); }
    private static BusinessException unauthorized() {return new BusinessException(401,"游客会话已失效，请登录或注册后继续");}
}
