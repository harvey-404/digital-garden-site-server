package com.harvey.digitalgarden.tracker;

import com.fasterxml.jackson.databind.JsonNode;

public interface TrackerGoalModel {
  JsonNode parse(String text);
}
