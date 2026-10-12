package com.harvey.digitalgarden.account;

import com.harvey.digitalgarden.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AccountChallengeService {
  private final JdbcTemplate db;
  private final AccountMailSender mail;
  private final AccountRateLimit rate;
  private final TransactionTemplate tx;
  private final String secret;
  private final int dailyLimit;

  public AccountChallengeService(
      JdbcTemplate db,
      AccountMailSender mail,
      AccountRateLimit rate,
      org.springframework.transaction.PlatformTransactionManager manager,
      @Value("${app.account.code-secret:${app.jwt.secret}}") String secret,
      @Value("${app.account.mail.daily-limit:200}") int dailyLimit) {
    this.db = db;
    this.mail = mail;
    this.rate = rate;
    this.secret = secret;
    this.dailyLimit = dailyLimit;
    tx = new TransactionTemplate(manager);
    tx.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public void send(String rawEmail, String purpose, String ip) {
    String email = AccountSecurity.email(rawEmail);
    if (!"register".equals(purpose) && !"reset".equals(purpose))
      throw BusinessException.badRequest("验证码用途无效");
    if (!mail.configured()) throw new BusinessException(503, "邮箱服务尚未配置，请联系站长或稍后再试");
    rate.consume("mail-cooldown:" + email, 1, 60000);
    rate.consume("mail-email:" + email, 5, 3600000);
    rate.consume("mail-ip:" + ip, 20, 3600000);
    rate.consume("mail-global", dailyLimit, 86400000);
    int exists =
        db.queryForObject(
            "SELECT COUNT(*) FROM site_account WHERE email=? AND status='active'",
            Integer.class,
            email);
    // Same response for unknown reset addresses and already registered addresses.
    if (("reset".equals(purpose) && exists == 0) || ("register".equals(purpose) && exists > 0))
      return;
    String code = AccountSecurity.code();
    mail.sendCode(email, purpose, code);
    tx.executeWithoutResult(
        s -> {
          db.update("DELETE FROM site_email_challenge WHERE email=? AND purpose=?", email, purpose);
          db.update(
              "INSERT INTO"
                  + " site_email_challenge(email,purpose,code_hash,expires_at,attempts,consumed)"
                  + " VALUES(?,?,?,?,0,FALSE)",
              email,
              purpose,
              AccountSecurity.codeHash(secret, email, purpose, code),
              System.currentTimeMillis() + 600000);
          db.update(
              "DELETE FROM site_email_challenge WHERE expires_at<?",
              System.currentTimeMillis() - 86400000);
        });
  }

  public void consume(String email, String purpose, String code) {
    Boolean accepted =
        tx.execute(
            s -> {
              var rows =
                  db.queryForList(
                      "SELECT * FROM site_email_challenge WHERE email=? AND purpose=? FOR UPDATE",
                      email,
                      purpose);
              if (rows.isEmpty()) return false;
              var row = rows.getFirst();
              int attempts = ((Number) row.get("attempts")).intValue();
              if (Boolean.TRUE.equals(row.get("consumed"))
                  || attempts >= 5
                  || ((Number) row.get("expires_at")).longValue() < System.currentTimeMillis())
                return false;
              boolean match =
                  AccountSecurity.equal(
                      row.get("code_hash").toString(),
                      AccountSecurity.codeHash(secret, email, purpose, code == null ? "" : code));
              db.update(
                  "UPDATE site_email_challenge SET attempts=?,consumed=? WHERE email=? AND"
                      + " purpose=?",
                  attempts + 1,
                  match,
                  email,
                  purpose);
              return match;
            });
    if (!Boolean.TRUE.equals(accepted)) throw BusinessException.badRequest("验证码错误、已使用或已过期，请重新获取");
  }
}
