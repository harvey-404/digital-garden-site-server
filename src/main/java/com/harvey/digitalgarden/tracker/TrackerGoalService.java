package com.harvey.digitalgarden.tracker;

import static com.harvey.digitalgarden.tracker.TrackerTypes.*;

import com.harvey.digitalgarden.tracker.TrackerTypes.Period;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TrackerGoalService {
  final TrackerStore store;
  final TrackerGoalModel model;
  final TrackerGoalValidator validator;
  final TrackerActivityService activities;
  final TransactionTemplate tx;

  public TrackerGoalService(
      TrackerStore s,
      TrackerGoalModel m,
      TrackerGoalValidator v,
      TrackerActivityService a,
      PlatformTransactionManager manager) {
    store = s;
    model = m;
    validator = v;
    activities = a;
    tx = new TransactionTemplate(manager);
  }

  public NaturalResult create(long uid, NaturalInput input) {
    TrackerValidation.request(input.requestId());
    String text = TrackerValidation.text(input.text(), 2000, "目标描述");
    Long replay =
        tx.execute(
            status -> {
              var user = store.user(uid, true);
              Long existing = store.replay(uid, input.requestId(), "natural");
              if (existing != null) return existing;
              LocalDate day = LocalDate.now(ZoneId.of(user.timezone()));
              var rows =
                  store
                      .jdbc()
                      .queryForList(
                          "SELECT used FROM tracker_ai_quota WHERE user_id=? AND quota_day=?",
                          uid,
                          day);
              if (!rows.isEmpty() && ((Number) rows.getFirst().get("used")).intValue() >= 30)
                throw new TrackerException(429, "今日AI次数已用完，请使用手动创建");
              if (rows.isEmpty())
                store
                    .jdbc()
                    .update(
                        "INSERT INTO tracker_ai_quota(user_id,quota_day,used) VALUES(?,?,1)",
                        uid,
                        day);
              else
                store
                    .jdbc()
                    .update(
                        "UPDATE tracker_ai_quota SET used=used+1 WHERE user_id=? AND quota_day=?",
                        uid,
                        day);
              return null;
            });
    if (replay != null)
      return new NaturalResult("CREATED", "已保存", activities.view(uid, replay, null, Period.WEEK));
    var result = model.parse(text);
    if (result != null && result.isObject() && result.path("status").asText().equals("CLARIFY")) {
      String message = validator.string(result, "message", 300);
      return new NaturalResult("CLARIFY", message, null);
    }
    var activity =
        activities.createNatural(uid, validator.validate(input.requestId(), text, result));
    return new NaturalResult("CREATED", "已保存，可在活动菜单中撤销", activity);
  }
}
