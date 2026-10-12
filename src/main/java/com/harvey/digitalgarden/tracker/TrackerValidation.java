package com.harvey.digitalgarden.tracker;

import static com.harvey.digitalgarden.tracker.TrackerTypes.*;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

public final class TrackerValidation {
  private TrackerValidation() {}

  public static String text(String value, int max, String field) {
    if (value == null || value.strip().isEmpty() || value.strip().length() > max)
      throw TrackerException.bad(field + "格式不正确");
    return value.strip();
  }

  public static String username(String value) {
    String n = text(value, 32, "用户名").toLowerCase(Locale.ROOT);
    if (!n.matches("[a-z0-9_-]{3,32}")) throw TrackerException.bad("用户名需为3至32位字母、数字、下划线或短横线");
    return n;
  }

  public static String password(String value) {
    if (value == null
        || value.getBytes(StandardCharsets.UTF_8).length < 8
        || value.getBytes(StandardCharsets.UTF_8).length > 72)
      throw TrackerException.bad("密码需为8至72字节");
    return value;
  }

  public static String timezone(String value) {
    try {
      return ZoneId.of(value == null || value.isBlank() ? "Asia/Shanghai" : value).getId();
    } catch (Exception e) {
      throw TrackerException.bad("时区无效");
    }
  }

  public static UUID request(UUID id) {
    if (id == null) throw TrackerException.bad("缺少请求编号");
    return id;
  }

  public static void rule(RuleInput r) {
    if (r == null) return;
    if (r.period() == null
        || r.metric() == null
        || r.direction() == null
        || r.target() == null
        || r.target() < 1
        || r.target() > (r.metric() == Metric.COUNT ? 1000000 : 604800))
      throw TrackerException.bad("目标周期、指标、方向与正整数阈值必须完整");
    if ((r.sessionDirection() == null) != (r.sessionSeconds() == null)
        || r.sessionSeconds() != null && (r.sessionSeconds() < 1 || r.sessionSeconds() > 604800))
      throw TrackerException.bad("单次时长条件不完整");
  }

  public static void record(RecordInput r, boolean durationRequired, Instant now) {
    if (r.quantity() == null
        || r.quantity() < 1
        || r.quantity() > 10000
        || r.startedAt() == null
        || r.startedAt().isAfter(now)) throw TrackerException.bad("次数或开始时间无效");
    if (durationRequired && r.durationSeconds() == null) throw TrackerException.bad("此活动必须填写时长");
    if (r.durationSeconds() != null && (r.durationSeconds() < 0 || r.durationSeconds() > 604800))
      throw TrackerException.bad("时长需在0至7天内");
    if (r.durationSeconds() != null
        && r.startedAt().plusSeconds(r.durationSeconds()).isAfter(now.plusSeconds(5)))
      throw TrackerException.bad("记录结束时间不能在未来");
    if (r.note() != null && r.note().length() > 1000) throw TrackerException.bad("备注最多1000字");
  }
}
