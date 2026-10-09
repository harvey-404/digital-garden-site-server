package com.harvey.digitalgarden.tracker;

import static com.harvey.digitalgarden.tracker.TrackerTypes.*;

import com.harvey.digitalgarden.tracker.TrackerTypes.Period;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrackerRecordService {
  final TrackerStore store;

  public TrackerRecordService(TrackerStore s) {
    store = s;
  }

  public static RecordView view(Entry e) {
    return new RecordView(
        e.id(),
        e.activityId(),
        e.quantity(),
        e.startedAt(),
        e.durationSeconds(),
        e.note(),
        e.deleted());
  }

  public List<RecordView> list(long uid, long id, LocalDate date, Period period) {
    var u = store.user(uid, false);
    store.activity(uid, id);
    var zone = ZoneId.of(u.timezone());
    LocalDate from = TrackerSummary.start(date == null ? LocalDate.now(zone) : date, period);
    Instant a = from.atStartOfDay(zone).toInstant(),
        b = from.plusDays(period == Period.WEEK ? 7 : 1).atStartOfDay(zone).toInstant();
    return store.entries(uid, id).stream()
        .filter(
            e ->
                !e.startedAt().isBefore(a) && e.startedAt().isBefore(b)
                    || e.segments().stream()
                        .anyMatch(s -> s.start().isBefore(b) && s.end().isAfter(a)))
        .map(TrackerRecordService::view)
        .toList();
  }

  @Transactional
  public RecordView create(long uid, long activityId, RecordInput input) {
    store.user(uid, true);
    store.activity(uid, activityId);
    Long replay = store.replay(uid, input.requestId(), "record:" + activityId);
    if (replay != null) return view(store.entry(uid, replay));
    validate(uid, activityId, input);
    long secs = input.durationSeconds() == null ? 0 : input.durationSeconds();
    long id =
        insert(
            uid,
            activityId,
            input.quantity(),
            input.startedAt(),
            secs,
            input.note(),
            List.of(new Segment(input.startedAt(), input.startedAt().plusSeconds(secs))));
    store.remember(uid, input.requestId(), "record:" + activityId, id);
    return view(store.entry(uid, id));
  }

  long insert(
      long uid,
      long activityId,
      int quantity,
      Instant started,
      long seconds,
      String note,
      List<Segment> segments) {
    return store.insert(
        "INSERT INTO"
            + " tracker_record(user_id,activity_id,quantity,started_at,duration_seconds,note,deleted,segments)"
            + " VALUES(?,?,?,?,?,?,false,?)",
        uid,
        activityId,
        quantity,
        started.toString(),
        seconds,
        note == null ? "" : note,
        store.encode(segments));
  }

  @Transactional
  public RecordView edit(long uid, long id, RecordInput input) {
    store.user(uid, true);
    Entry e = store.entry(uid, id);
    validate(uid, e.activityId(), input);
    long sec = input.durationSeconds() == null ? 0 : input.durationSeconds();
    store
        .jdbc()
        .update(
            "UPDATE tracker_record SET quantity=?,started_at=?,duration_seconds=?,note=?,segments=?"
                + " WHERE user_id=? AND id=?",
            input.quantity(),
            input.startedAt().toString(),
            sec,
            input.note() == null ? "" : input.note(),
            store.encode(
                List.of(new Segment(input.startedAt(), input.startedAt().plusSeconds(sec)))),
            uid,
            id);
    return view(store.entry(uid, id));
  }

  void validate(long uid, long id, RecordInput input) {
    var a = store.activity(uid, id);
    if (a.archived()) throw TrackerException.conflict("请先恢复此活动");
    var zone = ZoneId.of(store.user(uid, false).timezone());
    LocalDate date =
        input.startedAt() == null
            ? LocalDate.now(zone)
            : input.startedAt().atZone(zone).toLocalDate();
    var r =
        store.rules(id).stream()
            .filter(x -> !x.effectiveFrom().isAfter(date))
            .max(Comparator.comparing(Rule::effectiveFrom).thenComparing(Rule::id))
            .orElse(null);
    TrackerValidation.record(input, r != null && r.sessionDirection() != null, Instant.now());
  }

  @Transactional
  public void deleted(long uid, long id, boolean deleted) {
    store.user(uid, true);
    store.entry(uid, id);
    store
        .jdbc()
        .update("UPDATE tracker_record SET deleted=? WHERE user_id=? AND id=?", deleted, uid, id);
  }
}
