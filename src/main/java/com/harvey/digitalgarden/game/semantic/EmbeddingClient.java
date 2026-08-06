package com.harvey.digitalgarden.game.semantic;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Semaphore;

@Service
public class EmbeddingClient implements EmbeddingPort {

    private final RestClient restClient;
    private final String apiKey;
    private final String embeddingModel;
    private final Semaphore semaphore;

    public EmbeddingClient(
            @Value("${app.dashscope.api-key}") String apiKey,
            @Value("${app.dashscope.embedding-model}") String embeddingModel,
            @Value("${app.dashscope.base-url}") String baseUrl,
            @Value("${app.game.semantic.embedding-max-concurrent}") int maxConcurrent) {
        this.apiKey = apiKey;
        this.embeddingModel = embeddingModel;
        this.semaphore = new Semaphore(maxConcurrent);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public float[] embed(String text) throws EmbeddingException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new EmbeddingException("DASHSCOPE_API_KEY not configured");
        }

        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EmbeddingException("embedding interrupted", e);
        }

        try {
            JsonNode response = restClient.post()
                    .uri("/embeddings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", embeddingModel, "input", text))
                    .retrieve()
                    .body(JsonNode.class);
            return parseEmbedding(response);
        } catch (RestClientException e) {
            throw new EmbeddingException("DashScope embedding request failed", e);
        } finally {
            semaphore.release();
        }
    }

    private float[] parseEmbedding(JsonNode response) throws EmbeddingException {
        if (response == null) {
            throw new EmbeddingException("DashScope response empty");
        }
        JsonNode data = response.path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw new EmbeddingException("DashScope response missing embedding data");
        }
        JsonNode embedding = data.get(0).path("embedding");
        if (!embedding.isArray() || embedding.isEmpty()) {
            throw new EmbeddingException("DashScope response missing embedding vector");
        }
        float[] vector = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            vector[i] = (float) embedding.get(i).asDouble();
        }
        return vector;
    }
}
