package com.harvey.digitalgarden.tracker;

import static com.harvey.digitalgarden.tracker.TrackerTypes.*;

import com.harvey.digitalgarden.tracker.TrackerTypes.Period;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrackerActivityService {
  final TrackerStore store;
  final TrackerSummary calculator;

  public TrackerActivityService(TrackerStore s, TrackerSummary c) {
    store = s;
    calculator = c;
  }

  public LocalDate today(User u) {
    return LocalDate.now(ZoneId.of(u.timezone()));
  }

  public Activity view(long uid, long id, LocalDate date, Period period) {
    var row = store.activity(uid, id);
    var user = store.user(uid, false);
    var rules = store.rules(id);
    return new Activity(
        id,
        row.name(),
        row.unit(),
        row.template(),
        row.trackDuration(),
        row.archived(),
        rules,
        calculator.calculate(
            date == null ? today(user) : date,
            period,
            ZoneId.of(user.timezone()),
            rules,
            store.entries(uid, id)));
  }

  public List<Activity> list(long uid, LocalDate date, Period period, boolean archived) {
    store.user(uid, false);
    return store.activities(uid, archived).stream()
        .map(a -> view(uid, a.id(), date, period))
        .toList();
  }

  @Transactional
  public Activity create(long uid, ActivityInput input) {
    return createInternal(uid, input, "activity");
  }

  @Transactional
  public Activity createNatural(long uid, ActivityInput input) {
    return createInternal(uid, input, "natural");
  }

  private Activity createInternal(long uid, ActivityInput input, String operation) {
    User user = store.user(uid, true);
    Long replay = store.replay(uid, input.requestId(), operation);
    if (replay != null) return view(uid, replay, null, Period.WEEK);
    String name = TrackerValidation.text(input.name(), 80, "活动名称"),
        unit = TrackerValidation.text(input.unit(), 20, "单位"),
        template = TrackerValidation.text(input.template(), 40, "模板");
    TrackerValidation.rule(input.rule());
    unique(uid, name, null);
    long id =
        store.insert(
            "INSERT INTO"
                + " tracker_activity(user_id,name,active_name,unit,template,track_duration,archived)"
                + " VALUES(?,?,?,?,?,?,false)",
            uid,
            name,
            name.toLowerCase(Locale.ROOT),
            unit,
            template,
            input.trackDuration()
                || input.rule() != null
                    && (input.rule().metric() == Metric.DURATION
                        || input.rule().sessionDirection() != null));
    if (input.rule() != null) store.addRule(id, today(user), input.rule());
    store.remember(uid, input.requestId(), operation, id);
    return view(uid, id, null, Period.WEEK);
  }

  @Transactional
  public Activity edit(long uid, long id, ActivityEdit input) {
    User u = store.user(uid, true);
    var row = store.activity(uid, id);
    String name = TrackerValidation.text(input.name(), 80, "活动名称"),
        unit = TrackerValidation.text(input.unit(), 20, "单位");
    TrackerValidation.rule(input.rule());
    if (!row.archived()) unique(uid, name, id);
    List<Rule> rules = store.rules(id);
    Rule current =
        rules.stream()
            .filter(r -> !r.effectiveFrom().isAfter(today(u)))
            .max(Comparator.comparing(Rule::effectiveFrom).thenComparing(Rule::id))
            .orElse(null);
    Period p =
        current != null && current.period() != null
            ? current.period()
            : input.rule() != null ? input.rule().period() : Period.WEEK;
    LocalDate from = TrackerSummary.start(today(u), p).plusDays(p == Period.WEEK ? 7 : 1);
    // Never replace past versions; repeated edits append a deterministic latest version.
    store.addRule(id, from, input.rule());
    boolean duration =
        row.trackDuration()
            || input.rule() != null
                && (input.rule().metric() == Metric.DURATION
                    || input.rule().sessionDirection() != null);
    store
        .jdbc()
        .update(
            "UPDATE tracker_activity SET name=?,active_name=?,unit=?,track_duration=? WHERE"
                + " user_id=? AND id=?",
            name,
            row.archived() ? null : name.toLowerCase(Locale.ROOT),
            unit,
            duration,
            uid,
            id);
    return view(uid, id, null, Period.WEEK);
  }

  @Transactional
  public void archive(long uid, long id, boolean archive) {
    store.user(uid, true);
    var row = store.activity(uid, id);
    if (!archive) unique(uid, row.name(), id);
    if (archive && store.activeTimer(uid) != null && store.activeTimer(uid).activityId() == id)
      throw TrackerException.conflict("请先结束或取消此活动的计时");
    store
        .jdbc()
        .update(
            "UPDATE tracker_activity SET archived=?,active_name=? WHERE user_id=? AND id=?",
            archive,
            archive ? null : row.name().toLowerCase(Locale.ROOT),
            uid,
            id);
  }

  void unique(long uid, String name, Long except) {
    Long count =
        store
            .jdbc()
            .queryForObject(
                "SELECT COUNT(*) FROM tracker_activity WHERE user_id=? AND active_name=? AND id<>?",
                Long.class,
                uid,
                name.toLowerCase(Locale.ROOT),
                except == null ? -1 : except);
    if (count > 0) throw TrackerException.conflict("已有同名活动，请编辑原活动或使用其他名称");
  }
}
