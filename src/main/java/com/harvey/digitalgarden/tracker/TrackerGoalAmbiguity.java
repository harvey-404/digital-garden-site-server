package com.harvey.digitalgarden.tracker;

/** An understandable but incomplete or unsupported instruction must never auto-create a goal. */
public class TrackerGoalAmbiguity extends TrackerException {
  public TrackerGoalAmbiguity(String message) {
    super(422, message);
  }
}
