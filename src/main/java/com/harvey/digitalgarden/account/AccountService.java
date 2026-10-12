package com.harvey.digitalgarden.account;

import com.harvey.digitalgarden.common.BusinessException;
import java.sql.Statement;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
  public record Account(long id, String email, String displayName, String timezone) {}

  public record View(
      long id, String email, String displayName, String timezone, Map<String, Boolean> links) {}

  public record Login(View user, String token) {}

  private final JdbcTemplate db;
  private final PasswordEncoder passwords;
  private final AccountChallengeService codes;
  private final AccountRateLimit rate;
  private final long ttl;
  private final String dummyPassword;

  public AccountService(
      JdbcTemplate db,
      PasswordEncoder passwords,
      AccountChallengeService codes,
      AccountRateLimit rate,
      @Value("${app.account.session-days:30}") int days) {
    this.db = db;
    this.passwords = passwords;
    this.codes = codes;
    this.rate = rate;
    ttl = Math.max(1, Math.min(days, 90)) * 86400000L;
    dummyPassword = passwords.encode(AccountSecurity.randomToken());
  }

  @Transactional
  public Login register(
      String rawEmail,
      String password,
      String code,
      String displayName,
      String timezone,
      String ip) {
    String email = AccountSecurity.email(rawEmail);
    AccountSecurity.password(password);
    String name = AccountSecurity.displayName(displayName, email),
        zone = AccountSecurity.timezone(timezone);
    rate.consume("register-ip:" + ip, 30, 3600000);
    if (db.queryForObject("SELECT COUNT(*) FROM site_account WHERE email=?", Integer.class, email)
        > 0) throw BusinessException.conflict("该邮箱已注册，请直接登录或找回密码");
    codes.consume(email, "register", code);
    var key = new GeneratedKeyHolder();
    try {
      String encoded = passwords.encode(password);
      db.update(
          c -> {
            var s =
                c.prepareStatement(
                    "INSERT INTO"
                        + " site_account(email,password_hash,display_name,timezone,status,created_at)"
                        + " VALUES(?,?,?,?,'active',?)",
                    Statement.RETURN_GENERATED_KEYS);
            s.setString(1, email);
            s.setString(2, encoded);
            s.setString(3, name);
            s.setString(4, zone);
            s.setLong(5, System.currentTimeMillis());
            return s;
          },
          key);
    } catch (DuplicateKeyException e) {
      throw BusinessException.conflict("该邮箱已注册，请直接登录");
    }
    return session(
        new Account(Objects.requireNonNull(key.getKey()).longValue(), email, name, zone));
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Login login(String rawEmail, String password, String ip) {
    String email = AccountSecurity.email(rawEmail);
    AccountSecurity.password(password);
    rate.consume("login-ip:" + ip, 40, 3600000);
    rate.consume("login-email:" + email, 20, 3600000);
    // Serialize credential verification/session creation with password resets.
    var rows = db.queryForList("SELECT * FROM site_account WHERE email=? FOR UPDATE", email);
    String hash = rows.isEmpty() ? dummyPassword : rows.getFirst().get("password_hash").toString();
    boolean matches = passwords.matches(password, hash);
    if (!matches || rows.isEmpty() || !"active".equals(rows.getFirst().get("status")))
      throw new BusinessException(401, "邮箱或密码不正确");
    return session(account(rows.getFirst()));
  }

  private Login session(Account a) {
    long now = System.currentTimeMillis();
    String token = AccountSecurity.randomToken();
    db.update("DELETE FROM site_session WHERE expires_at<?", now);
    db.update(
        "INSERT INTO site_session(token_hash,account_id,expires_at,created_at) VALUES(?,?,?,?)",
        AccountSecurity.hash(token),
        a.id(),
        now + ttl,
        now);
    return new Login(view(a), token);
  }

  public Account authenticate(String token) {
    if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw unauthorized();
    var rows =
        db.queryForList(
            "SELECT a.* FROM site_account a JOIN site_session s ON s.account_id=a.id WHERE"
                + " s.token_hash=? AND s.expires_at>? AND a.status='active'",
            AccountSecurity.hash(token),
            System.currentTimeMillis());
    if (rows.isEmpty()) throw unauthorized();
    return account(rows.getFirst());
  }

  public View view(Account a) {
    var links = new LinkedHashMap<String, Boolean>();
    links.put("ledger", false);
    links.put("tracker", false);
    for (var row :
        db.queryForList("SELECT module FROM site_account_link WHERE account_id=?", a.id()))
      links.put(row.get("module").toString(), true);
    return new View(a.id(), a.email(), a.displayName(), a.timezone(), links);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void reset(String rawEmail, String code, String password, String ip) {
    String email = AccountSecurity.email(rawEmail);
    AccountSecurity.password(password);
    rate.consume("reset-ip:" + ip, 20, 3600000);
    codes.consume(email, "reset", code);
    var ids =
        db.queryForList(
            "SELECT id FROM site_account WHERE email=? AND status='active' FOR UPDATE",
            Long.class,
            email);
    if (ids.isEmpty()) throw BusinessException.badRequest("验证码错误、已使用或已过期，请重新获取");
    long id = ids.getFirst();
    db.update("UPDATE site_account SET password_hash=? WHERE id=?", passwords.encode(password), id);
    db.update("DELETE FROM site_session WHERE account_id=?", id);
  }

  public void logout(String token) {
    if (token != null)
      db.update("DELETE FROM site_session WHERE token_hash=?", AccountSecurity.hash(token));
  }

  public long ttlSeconds() {
    return ttl / 1000;
  }

  static BusinessException unauthorized() {
    return new BusinessException(401, "请登录后继续");
  }

  private Account account(Map<String, Object> row) {
    return new Account(
        ((Number) row.get("id")).longValue(),
        row.get("email").toString(),
        row.get("display_name").toString(),
        row.get("timezone").toString());
  }
}
