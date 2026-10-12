package com.harvey.digitalgarden.account;

import com.harvey.digitalgarden.common.BusinessException;
import com.harvey.digitalgarden.ledger.LedgerInviteUtil;
import com.harvey.digitalgarden.security.*;
import com.harvey.digitalgarden.service.ledger.LedgerUserAdminService;
import com.harvey.digitalgarden.tracker.TrackerPrincipal;
import java.sql.Statement;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountLinks {
  private final JdbcTemplate db;
  private final JwtUtil jwt;
  private final PasswordEncoder passwords;
  private final LedgerUserAdminService ledger;

  public AccountLinks(
      JdbcTemplate db, JwtUtil jwt, PasswordEncoder passwords, LedgerUserAdminService ledger) {
    this.db = db;
    this.jwt = jwt;
    this.passwords = passwords;
    this.ledger = ledger;
  }

  public boolean isBound(String module, long uid) {
    return db.queryForObject(
            "SELECT COUNT(*) FROM site_account_link WHERE module=? AND local_user_id=?",
            Integer.class,
            module,
            uid)
        > 0;
  }

  @Transactional
  public Object resolve(AccountService.Account a, String module) {
    lockAccount(a.id());
    Long uid = linked(a.id(), module);
    if (uid == null) {
      if ("tracker".equals(module)) {
        var key = new GeneratedKeyHolder();
        String name = "site_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24),
            hash = passwords.encode(AccountSecurity.randomToken());
        db.update(
            c -> {
              var s =
                  c.prepareStatement(
                      "INSERT INTO tracker_user(username,password_hash,timezone) VALUES(?,?,?)",
                      Statement.RETURN_GENERATED_KEYS);
              s.setString(1, name);
              s.setString(2, hash);
              s.setString(3, a.timezone());
              return s;
            },
            key);
        uid = Objects.requireNonNull(key.getKey()).longValue();
      } else if ("ledger".equals(module)) {
        var created = ledger.createUser();
        uid =
            db.queryForObject(
                "SELECT id FROM ledger_user WHERE user_sn=?", Long.class, created.getUserSn());
      } else throw BusinessException.badRequest("未知工具");
      db.update(
          "INSERT INTO site_account_link(account_id,module,local_user_id) VALUES(?,?,?)",
          a.id(),
          module,
          uid);
    }
    return principal(module, uid);
  }

  @Transactional
  public void bind(
      AccountService.Account a,
      String module,
      String token,
      String inviteCode,
      String username,
      String password) {
    lockAccount(a.id());
    long target = proof(module, token, inviteCode, username, password);
    // Lock the legacy row so two site accounts cannot claim it concurrently.
    String table = "ledger".equals(module) ? "ledger_user" : "tracker_user";
    db.queryForObject("SELECT id FROM " + table + " WHERE id=? FOR UPDATE", Long.class, target);
    var owners =
        db.queryForList(
            "SELECT account_id FROM site_account_link WHERE module=? AND local_user_id=?",
            Long.class,
            module,
            target);
    if (!owners.isEmpty() && owners.getFirst() != a.id())
      throw BusinessException.conflict("这个旧账号已经绑定了其他邮箱账号");
    Long current = linked(a.id(), module);
    if (current != null && current == target) return;
    if (current != null && !empty(module, current, a.timezone()))
      throw BusinessException.conflict("当前工具账号已有数据，无法自动合并。两份数据都已保留，请联系站长协助迁移");
    try {
      if (current != null)
        db.update("DELETE FROM site_account_link WHERE account_id=? AND module=?", a.id(), module);
      db.update(
          "INSERT INTO site_account_link(account_id,module,local_user_id) VALUES(?,?,?)",
          a.id(),
          module,
          target);
    } catch (DuplicateKeyException e) {
      throw BusinessException.conflict("这个旧账号已经绑定了其他邮箱账号");
    }
  }

  private long proof(
      String module, String token, String inviteCode, String username, String password) {
    if (!"ledger".equals(module) && !"tracker".equals(module))
      throw BusinessException.badRequest("未知工具");
    Long uid = null;
    if (token != null && !token.isBlank()) {
      try {
        var claims = jwt.parseClaims(token);
        if (!module.toUpperCase(Locale.ROOT).equals(claims.get("role", String.class)))
          throw new IllegalArgumentException();
        uid = claims.get("uid", Number.class).longValue();
      } catch (Exception e) {
        throw new BusinessException(401, "原登录凭证已失效，请使用原邀请码或用户名密码绑定");
      }
    } else if ("ledger".equals(module)) {
      String code;
      try {
        code = LedgerInviteUtil.normalizeInviteCode(inviteCode == null ? "" : inviteCode);
      } catch (Exception e) {
        throw BusinessException.badRequest("请输入有效的原记账邀请码");
      }
      var rows =
          db.queryForList(
              "SELECT id FROM ledger_user WHERE invite_code=? AND status='active' AND is_deleted=0",
              Long.class,
              code);
      if (!rows.isEmpty()) uid = rows.getFirst();
    } else {
      var rows =
          db.queryForList(
              "SELECT id,password_hash FROM tracker_user WHERE username=?",
              username == null ? "" : username.strip());
      if (!rows.isEmpty()
          && password != null
          && passwords.matches(password, rows.getFirst().get("password_hash").toString()))
        uid = ((Number) rows.getFirst().get("id")).longValue();
    }
    if (uid == null) throw new BusinessException(401, "原账号信息不正确，请检查后重试");
    principal(module, uid);
    return uid;
  }

  private Object principal(String module, long uid) {
    if ("tracker".equals(module)) {
      var rows = db.queryForList("SELECT username FROM tracker_user WHERE id=?", String.class, uid);
      if (rows.isEmpty()) throw new BusinessException(403, "原打卡账号不存在");
      return new TrackerPrincipal(uid, rows.getFirst());
    }
    var rows =
        db.queryForList(
            "SELECT user_sn FROM ledger_user WHERE id=? AND status='active' AND is_deleted=0",
            String.class,
            uid);
    if (rows.isEmpty()) throw new BusinessException(403, "账本账号不可用，请联系站长");
    return new LedgerPrincipal(uid, rows.getFirst());
  }

  private void lockAccount(long id) {
    db.queryForObject("SELECT id FROM site_account WHERE id=? FOR UPDATE", Long.class, id);
  }

  private Long linked(long id, String module) {
    var rows =
        db.queryForList(
            "SELECT local_user_id FROM site_account_link WHERE account_id=? AND module=?",
            Long.class,
            id,
            module);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  private boolean empty(String module, long uid, String initialTimezone) {
    if ("tracker".equals(module))
      return db.queryForObject(
                  "SELECT COUNT(*) FROM tracker_user WHERE id=? AND timezone=?",
                  Integer.class,
                  uid,
                  initialTimezone)
              == 1
          && db.queryForObject(
                  "SELECT COUNT(*) FROM tracker_activity WHERE user_id=?", Integer.class, uid)
              == 0;
    var profile =
        db.queryForMap(
            "SELECT display_name,default_budget_amount FROM ledger_user WHERE id=?", uid);
    if (!profile.get("display_name").toString().isEmpty()
        || new java.math.BigDecimal(profile.get("default_budget_amount").toString()).signum() != 0)
      return false;
    var defaults = LedgerUserAdminService.defaultCategoryNames();
    var categories =
        db.queryForList(
            "SELECT name,icon,sort_order,is_deleted FROM ledger_category WHERE user_id=? ORDER BY"
                + " sort_order,id",
            uid);
    if (categories.size() != defaults.size()) return false;
    for (int i = 0; i < defaults.size(); i++) {
      var category = categories.get(i);
      Object deleted = category.get("is_deleted");
      if (!defaults.get(i).equals(category.get("name"))
          || !"".equals(category.get("icon"))
          || ((Number) category.get("sort_order")).intValue() != i
          || Boolean.TRUE.equals(deleted)
          || deleted instanceof Number n && n.intValue() != 0) return false;
    }
    for (String table :
        List.of(
            "ledger_expense",
            "ledger_budget",
            "ledger_chat_message",
            "ledger_chat_attachment",
            "ledger_chat_draft"))
      if (db.queryForObject(
              "SELECT COUNT(*) FROM " + table + " WHERE user_id=?", Integer.class, uid)
          > 0) return false;
    return true;
  }
}
