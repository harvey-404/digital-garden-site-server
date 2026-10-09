package com.harvey.digitalgarden.tracker;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DashscopeTrackerGoalModel implements TrackerGoalModel {
  final RestClient client;
  final ObjectMapper json;
  final String key, model, prompt;
  final Semaphore slots = new Semaphore(2);

  public DashscopeTrackerGoalModel(
      ObjectMapper json,
      @Value("${app.dashscope.api-key:}") String key,
      @Value("${app.dashscope.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
          String url,
      @Value("${app.tracker.ai-model:qwen-plus}") String model)
      throws java.io.IOException {
    this.json = json;
    this.key = key;
    this.model = model;
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(10));
    factory.setReadTimeout(Duration.ofSeconds(45));
    client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    prompt =
        new ClassPathResource("prompts/tracker-goal-system.txt")
            .getContentAsString(StandardCharsets.UTF_8);
  }

  public JsonNode parse(String text) {
    if (key.isBlank()) throw new TrackerException(503, "AI暂不可用，请使用手动创建");
    if (!slots.tryAcquire()) throw new TrackerException(429, "AI正在忙，请稍后重试或手动创建");
    try {
      var body =
          Map.of(
              "model",
              model,
              "stream",
              false,
              "temperature",
              0,
              "max_tokens",
              1600,
              "response_format",
              Map.of("type", "json_object"),
              "messages",
              List.of(
                  Map.of("role", "system", "content", prompt),
                  Map.of("role", "user", "content", text)));
      JsonNode response =
          client
              .post()
              .uri("/chat/completions")
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(JsonNode.class);
      if (response == null
          || !response.path("choices").isArray()
          || response.path("choices").isEmpty()) throw new IllegalArgumentException();
      var choice = response.path("choices").get(0);
      if (!"stop".equals(choice.path("finish_reason").asText()))
        throw new IllegalArgumentException();
      String raw = choice.path("message").path("content").asText();
      if (raw.length() > 12000) throw new IllegalArgumentException();
      return json.readTree(raw);
    } catch (Exception e) {
      throw new TrackerException(503, "AI暂不可用，请稍后重试或使用手动创建");
    } finally {
      slots.release();
    }
  }
}
