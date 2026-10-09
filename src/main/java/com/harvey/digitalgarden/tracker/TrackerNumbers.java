package com.harvey.digitalgarden.tracker;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;

public final class TrackerNumbers {
  private TrackerNumbers() {}

  public static class LongValue extends JsonDeserializer<Long> {
    public Long deserialize(JsonParser p, DeserializationContext c) throws IOException {
      if (!p.currentToken().isNumeric() || !p.currentToken().name().equals("VALUE_NUMBER_INT"))
        throw JsonMappingException.from(p, "Expected integer");
      return p.getLongValue();
    }
  }

  public static class IntValue extends JsonDeserializer<Integer> {
    public Integer deserialize(JsonParser p, DeserializationContext c) throws IOException {
      if (!p.currentToken().isNumeric() || !p.currentToken().name().equals("VALUE_NUMBER_INT"))
        throw JsonMappingException.from(p, "Expected integer");
      return p.getIntValue();
    }
  }
}
