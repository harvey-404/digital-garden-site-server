package com.harvey.digitalgarden.tracker;

import static com.harvey.digitalgarden.tracker.TrackerTypes.*;

import com.harvey.digitalgarden.security.JwtUtil;
import java.time.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrackerAuthService {
  final TrackerStore store;
  final PasswordEncoder passwords;
  final JwtUtil jwt;
  final Map<String, Window> attempts = new LinkedHashMap<>();

  record Window(long since, int count) {}

  public TrackerAuthService(TrackerStore s, PasswordEncoder p, JwtUtil j) {
    store = s;
    passwords = p;
    jwt = j;
  }

  public synchronized void throttle(String ip) {
    long now = System.currentTimeMillis();
    attempts.entrySet().removeIf(e -> now - e.getValue().since() > 600000);
    Window w = attempts.get(ip);
    if (w != null && w.count() >= 30) throw new TrackerException(429, "尝试过于频繁，请10分钟后重试");
    if (w == null && attempts.size() >= 10000) throw new TrackerException(429, "服务繁忙，请稍后重试");
    attempts.put(ip, new Window(w == null ? now : w.since(), w == null ? 1 : w.count() + 1));
  }

  @Transactional
  public Auth register(Credentials c) {
    String name = TrackerValidation.username(c.username()),
        pass = TrackerValidation.password(c.password()),
        zone = TrackerValidation.timezone(c.timezone());
    if (store
            .jdbc()
            .queryForObject("SELECT COUNT(*) FROM tracker_user WHERE username=?", Long.class, name)
        > 0) throw TrackerException.conflict("用户名已被使用");
    long id =
        store.insert(
            "INSERT INTO tracker_user(username,password_hash,timezone) VALUES(?,?,?)",
            name,
            passwords.encode(pass),
            zone);
    return auth(new User(id, name, zone));
  }

  public Auth login(Credentials c) {
    String name = TrackerValidation.username(c.username());
    TrackerValidation.password(c.password());
    var rows =
        store
            .jdbc()
            .queryForList("SELECT id,password_hash FROM tracker_user WHERE username=?", name);
    if (rows.isEmpty()
        || !passwords.matches(c.password(), rows.getFirst().get("password_hash").toString()))
      throw new TrackerException(401, "用户名或密码不正确");
    if (store.jdbc().queryForObject("SELECT COUNT(*) FROM site_account_link WHERE module='tracker' AND local_user_id=?", Integer.class, ((Number) rows.getFirst().get("id")).longValue()) > 0)
      throw new TrackerException(403, "此账号已绑定邮箱，请使用全站邮箱账号登录");
    return auth(store.user(((Number) rows.getFirst().get("id")).longValue(), false));
  }

  private Auth auth(User user) {
    return new Auth(jwt.generateTrackerToken(user.id(), user.username()), user);
  }

  public User me(long uid) {
    return store.user(uid, false);
  }

  @Transactional
  public User timezone(long uid, String zone) {
    User old = store.user(uid, true);
    String valid = TrackerValidation.timezone(zone);
    if (valid.equals(old.timezone())) return old;
    if (store
                .jdbc()
                .queryForObject(
                    "SELECT COUNT(*) FROM tracker_record WHERE user_id=?", Long.class, uid)
            > 0
        || store
                .jdbc()
                .queryForObject(
                    "SELECT COUNT(*) FROM tracker_timer WHERE user_id=?", Long.class, uid)
            > 0) throw TrackerException.conflict("已有记录或计时器，不能更改时区");
    store.jdbc().update("UPDATE tracker_user SET timezone=? WHERE id=?", valid, uid);
    return store.user(uid, false);
  }
}
