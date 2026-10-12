package com.harvey.digitalgarden.account;

import com.harvey.digitalgarden.common.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AccountRateLimit {
  private final JdbcTemplate db;

  public AccountRateLimit(JdbcTemplate db) {
    this.db = db;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void consume(String key, int limit, long windowMillis) {
    long now = System.currentTimeMillis();
    String bucket = AccountSecurity.hash(key);
    try {
      db.update(
          "INSERT INTO site_account_rate(bucket,window_start,attempts) SELECT ?,?,0 WHERE NOT"
              + " EXISTS(SELECT 1 FROM site_account_rate WHERE bucket=?)",
          bucket,
          now,
          bucket);
    } catch (DuplicateKeyException ignored) {
      /* Concurrent first request created the bucket. */
    }
    var row =
        db.queryForMap(
            "SELECT window_start,attempts FROM site_account_rate WHERE bucket=? FOR UPDATE",
            bucket);
    long start = ((Number) row.get("window_start")).longValue();
    int count = ((Number) row.get("attempts")).intValue();
    if (now - start >= windowMillis) {
      start = now;
      count = 0;
    }
    if (count >= limit) throw new BusinessException(429, "操作过于频繁，请稍后再试");
    db.update(
        "UPDATE site_account_rate SET window_start=?,attempts=? WHERE bucket=?",
        start,
        count + 1,
        bucket);
  }
}
