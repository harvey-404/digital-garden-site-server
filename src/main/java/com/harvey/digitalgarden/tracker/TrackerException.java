package com.harvey.digitalgarden.tracker;

public class TrackerException extends RuntimeException {
  final int code;

  public TrackerException(int code, String message) {
    super(message);
    this.code = code;
  }

  public static TrackerException bad(String message) {
    return new TrackerException(400, message);
  }

  public static TrackerException missing() {
    return new TrackerException(404, "记录不存在");
  }

  public static TrackerException conflict(String message) {
    return new TrackerException(409, message);
  }
}
