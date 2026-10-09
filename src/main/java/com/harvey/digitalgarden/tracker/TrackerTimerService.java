package com.harvey.digitalgarden.tracker;

import static com.harvey.digitalgarden.tracker.TrackerTypes.*;

import com.harvey.digitalgarden.tracker.TrackerTypes.Timer;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrackerTimerService {
  final TrackerStore store;
  final TrackerRecordService records;

  public TrackerTimerService(TrackerStore s, TrackerRecordService r) {
    store = s;
    records = r;
  }

  List<Segment> segments(TrackerStore.TimerRow t, Instant now) {
    List<Segment> result = new ArrayList<>(t.segments());
    if (t.runningSince() != null && now.isAfter(t.runningSince()))
      result.add(new Segment(t.runningSince(), now));
    return result;
  }

  long elapsed(List<Segment> s) {
    return s.stream().mapToLong(x -> Duration.between(x.start(), x.end()).getSeconds()).sum();
  }

  Timer view(TrackerStore.TimerRow t) {
    if (!Set.of("RUNNING", "PAUSED").contains(t.state()))
      throw TrackerException.conflict("计时已结束，请刷新记录");
    return new Timer(
        t.id(),
        t.activityId(),
        t.startedAt(),
        t.runningSince(),
        elapsed(segments(t, Instant.now())),
        t.state());
  }

  public Timer get(long uid) {
    store.user(uid, false);
    var t = store.activeTimer(uid);
    return t == null ? null : view(t);
  }

  @Transactional
  public Timer start(long uid, long activity, UUID request) {
    store.user(uid, true);
    var a = store.activity(uid, activity);
    Long replay = store.replay(uid, request, "timer:" + activity);
    if (replay != null) return view(store.timer(uid, replay));
    if (a.archived()) throw TrackerException.conflict("请先恢复此活动");
    if (!a.trackDuration()) throw TrackerException.bad("此活动未启用计时");
    if (store.activeTimer(uid) != null) throw TrackerException.conflict("已有进行中的计时，请先完成或取消");
    Instant now = Instant.now();
    long id =
        store.insert(
            "INSERT INTO"
                + " tracker_timer(user_id,activity_id,started_at,running_since,segments,state,active_user)"
                + " VALUES(?,?,?,?,?,'RUNNING',?)",
            uid,
            activity,
            now.toString(),
            now.toString(),
            "[]",
            uid);
    store.remember(uid, request, "timer:" + activity, id);
    return view(store.timer(uid, id));
  }

  @Transactional
  public Timer transition(long uid, long id, boolean resume) {
    store.user(uid, true);
    var t = store.timer(uid, id);
    view(t);
    if (resume && t.state().equals("PAUSED"))
      store
          .jdbc()
          .update(
              "UPDATE tracker_timer SET state='RUNNING',running_since=? WHERE id=?",
              Instant.now().toString(),
              id);
    if (!resume && t.state().equals("RUNNING"))
      store
          .jdbc()
          .update(
              "UPDATE tracker_timer SET state='PAUSED',running_since=NULL,segments=? WHERE id=?",
              store.encode(segments(t, Instant.now())),
              id);
    return view(store.timer(uid, id));
  }

  @Transactional
  public RecordView finish(long uid, long id) {
    store.user(uid, true);
    var t = store.timer(uid, id);
    if (t.recordId() != null) return TrackerRecordService.view(store.entry(uid, t.recordId()));
    view(t);
    var segments = segments(t, Instant.now());
    long seconds = elapsed(segments);
    if (seconds > 604800) throw TrackerException.bad("计时超过7天，请取消后分次手动记录");
    long record = records.insert(uid, t.activityId(), 1, t.startedAt(), seconds, "", segments);
    store
        .jdbc()
        .update(
            "UPDATE tracker_timer SET"
                + " state='FINISHED',running_since=NULL,segments=?,record_id=?,active_user=NULL"
                + " WHERE id=?",
            store.encode(segments),
            record,
            id);
    return TrackerRecordService.view(store.entry(uid, record));
  }

  @Transactional
  public void cancel(long uid, long id) {
    store.user(uid, true);
    var t = store.timer(uid, id);
    if (t.state().equals("FINISHED")) throw TrackerException.conflict("计时已保存，可在历史记录撤销");
    store
        .jdbc()
        .update(
            "UPDATE tracker_timer SET state='CANCELLED',running_since=NULL,active_user=NULL WHERE"
                + " id=?",
            id);
  }
}
