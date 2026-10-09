package com.harvey.digitalgarden.tracker;

import static com.harvey.digitalgarden.tracker.TrackerTypes.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.*;
import org.springframework.stereotype.Repository;

@Repository
public class TrackerStore {
  final JdbcTemplate db;
  final ObjectMapper json;

  public TrackerStore(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  public JdbcTemplate jdbc() {
    return db;
  }

  public long insert(String sql, Object... values) {
    KeyHolder key = new GeneratedKeyHolder();
    db.update(
        c -> {
          PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
          for (int i = 0; i < values.length; i++) p.setObject(i + 1, values[i]);
          return p;
        },
        key);
    return Objects.requireNonNull(key.getKey()).longValue();
  }

  public String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public <T> T decode(String s, Class<T> type) {
    try {
      return json.readValue(s, type);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public List<Segment> segments(String s) {
    try {
      return json.readValue(s, new TypeReference<List<Segment>>() {});
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public User user(long uid, boolean lock) {
    return db
        .query(
            "SELECT * FROM tracker_user WHERE id=?" + (lock ? " FOR UPDATE" : ""),
            (r, n) -> new User(r.getLong("id"), r.getString("username"), r.getString("timezone")),
            uid)
        .stream()
        .findFirst()
        .orElseThrow(TrackerException::missing);
  }

  public record ActivityRow(
      long id,
      long userId,
      String name,
      String unit,
      String template,
      boolean trackDuration,
      boolean archived) {}

  private final RowMapper<ActivityRow> activityMapper =
      (r, n) ->
          new ActivityRow(
              r.getLong("id"),
              r.getLong("user_id"),
              r.getString("name"),
              r.getString("unit"),
              r.getString("template"),
              r.getBoolean("track_duration"),
              r.getBoolean("archived"));

  public ActivityRow activity(long uid, long id) {
    return db
        .query("SELECT * FROM tracker_activity WHERE user_id=? AND id=?", activityMapper, uid, id)
        .stream()
        .findFirst()
        .orElseThrow(TrackerException::missing);
  }

  public List<ActivityRow> activities(long uid, boolean archived) {
    return db.query(
        "SELECT * FROM tracker_activity WHERE user_id=? AND archived=? ORDER BY id",
        activityMapper,
        uid,
        archived);
  }

  public List<Rule> rules(long id) {
    return db.query(
        "SELECT * FROM tracker_rule WHERE activity_id=? ORDER BY effective_from,id",
        (r, n) -> {
          RuleInput b = decode(r.getString("body"), RuleInput.class);
          return new Rule(
              r.getLong("id"),
              b == null ? null : b.period(),
              b == null ? null : b.metric(),
              b == null ? null : b.direction(),
              b == null ? 0 : b.target(),
              b == null ? null : b.sessionDirection(),
              b == null ? null : b.sessionSeconds(),
              r.getDate("effective_from").toLocalDate());
        },
        id);
  }

  public void addRule(long id, LocalDate from, RuleInput rule) {
    insert(
        "INSERT INTO tracker_rule(activity_id,effective_from,body) VALUES(?,?,?)",
        id,
        java.sql.Date.valueOf(from),
        encode(rule));
  }

  private Entry entry(ResultSet r) throws SQLException {
    return new Entry(
        r.getLong("id"),
        r.getLong("activity_id"),
        r.getInt("quantity"),
        Instant.parse(r.getString("started_at")),
        r.getLong("duration_seconds"),
        r.getString("note"),
        r.getBoolean("deleted"),
        segments(r.getString("segments")));
  }

  public List<Entry> entries(long uid, long activity) {
    return db.query(
        "SELECT * FROM tracker_record WHERE user_id=? AND activity_id=? ORDER BY started_at DESC,id"
            + " DESC",
        (r, n) -> entry(r),
        uid,
        activity);
  }

  public Entry entry(long uid, long id) {
    return db
        .query("SELECT * FROM tracker_record WHERE user_id=? AND id=?", (r, n) -> entry(r), uid, id)
        .stream()
        .findFirst()
        .orElseThrow(TrackerException::missing);
  }

  public Long replay(long uid, UUID request, String operation) {
    TrackerValidation.request(request);
    var rows =
        db.queryForList(
            "SELECT operation,resource_id FROM tracker_request WHERE user_id=? AND request_id=?",
            uid,
            request.toString());
    if (rows.isEmpty()) return null;
    if (!operation.equals(rows.getFirst().get("OPERATION"))
        && !operation.equals(rows.getFirst().get("operation")))
      throw TrackerException.conflict("请求编号已用于其他操作");
    return ((Number)
            (rows.getFirst().containsKey("resource_id")
                ? rows.getFirst().get("resource_id")
                : rows.getFirst().get("RESOURCE_ID")))
        .longValue();
  }

  public void remember(long uid, UUID request, String operation, long id) {
    db.update(
        "INSERT INTO tracker_request(user_id,request_id,operation,resource_id) VALUES(?,?,?,?)",
        uid,
        request.toString(),
        operation,
        id);
  }

  public record TimerRow(
      long id,
      long activityId,
      Instant startedAt,
      Instant runningSince,
      List<Segment> segments,
      String state,
      Long recordId) {}

  private TimerRow timer(ResultSet r) throws SQLException {
    return new TimerRow(
        r.getLong("id"),
        r.getLong("activity_id"),
        Instant.parse(r.getString("started_at")),
        r.getString("running_since") == null ? null : Instant.parse(r.getString("running_since")),
        segments(r.getString("segments")),
        r.getString("state"),
        r.getObject("record_id") == null ? null : r.getLong("record_id"));
  }

  public TimerRow timer(long uid, long id) {
    return db
        .query("SELECT * FROM tracker_timer WHERE user_id=? AND id=?", (r, n) -> timer(r), uid, id)
        .stream()
        .findFirst()
        .orElseThrow(TrackerException::missing);
  }

  public TimerRow activeTimer(long uid) {
    return db
        .query("SELECT * FROM tracker_timer WHERE active_user=?", (r, n) -> timer(r), uid)
        .stream()
        .findFirst()
        .orElse(null);
  }
}
