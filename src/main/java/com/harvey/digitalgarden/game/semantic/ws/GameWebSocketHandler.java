package com.harvey.digitalgarden.game.semantic.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harvey.digitalgarden.game.semantic.EmbeddingException;
import com.harvey.digitalgarden.game.semantic.GameEngineService;
import com.harvey.digitalgarden.game.semantic.GameEngineService.GuessResult;
import com.harvey.digitalgarden.game.semantic.GameEngineService.RoundSnapshot;
import com.harvey.digitalgarden.game.semantic.GameEngineService.WordRecord;
import com.harvey.digitalgarden.game.semantic.RoundSnapshotChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Semantic guess WebSocket protocol. Outbound JSON is camelCase and must never include targetWord.
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private static final String ATTR_USERNAME = "username";
    private static final String ATTR_FP = "fp";
    private static final int MAX_USERNAME_LEN = 16;
    private static final int MAX_FP_LEN = 64;

    private final GameEngineService gameEngine;
    private final GuessRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final long nicknameCooldownMs;

    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    /** fp → last accepted display name + bind time (for rename cooldown). */
    private final ConcurrentHashMap<String, NickBinding> nickByFp = new ConcurrentHashMap<>();

    public GameWebSocketHandler(
            GameEngineService gameEngine,
            GuessRateLimiter rateLimiter,
            ObjectMapper objectMapper,
            @Value("${app.game.semantic.nickname-cooldown-seconds}") long nicknameCooldownSeconds) {
        this.gameEngine = gameEngine;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
        this.nicknameCooldownMs = Math.max(0L, nicknameCooldownSeconds) * 1000L;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Map<String, String> query = parseQuery(session.getUri());
        String requestedUsername = query.get("username");
        String fp = query.get("fp");

        if (requestedUsername == null || requestedUsername.isBlank()
                || requestedUsername.length() > MAX_USERNAME_LEN) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("invalid username"));
            return;
        }
        if (fp == null || fp.isBlank() || fp.length() > MAX_FP_LEN) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("invalid fp"));
            return;
        }

        String username = requestedUsername.trim();
        boolean nickCooldown = false;
        long now = System.currentTimeMillis();
        NickBinding existing = nickByFp.get(fp);
        if (existing != null
                && !existing.username.equals(username)
                && now - existing.boundAtMs < nicknameCooldownMs) {
            username = existing.username;
            nickCooldown = true;
        } else {
            nickByFp.put(fp, new NickBinding(username, now));
        }

        session.getAttributes().put(ATTR_USERNAME, username);
        session.getAttributes().put(ATTR_FP, fp);
        sessions.put(session.getId(), session);

        sendJson(session, roundStatePayload());
        sendJson(session, top10Payload());
        sendJson(session, hintsPayload());
        if (nickCooldown) {
            sendJson(session, errorPayload("NICK_COOLDOWN", "昵称冷却中，已沿用原昵称"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String username = (String) session.getAttributes().get(ATTR_USERNAME);
        String fp = (String) session.getAttributes().get(ATTR_FP);
        if (username == null || fp == null) {
            return;
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            sendJson(session, errorPayload("BAD_REQUEST", "invalid json"));
            return;
        }

        String type = textOrNull(root, "type");
        if (type == null && root.hasNonNull("guess")) {
            // brief Demo compatibility
            handleGuess(session, username, fp, root.get("guess").asText());
            return;
        }
        if (type == null) {
            sendJson(session, errorPayload("BAD_REQUEST", "missing type"));
            return;
        }

        switch (type) {
            case "ping" -> sendJson(session, Map.of("type", "pong"));
            case "guess" -> handleGuess(session, username, fp, textOrNull(root, "word"));
            default -> sendJson(session, errorPayload("BAD_REQUEST", "unknown type"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        rateLimiter.onSessionClosed(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("ws transport error session={}", session.getId(), exception);
        sessions.remove(session.getId());
        rateLimiter.onSessionClosed(session.getId());
        try {
            if (session.isOpen()) {
                session.close(CloseStatus.SERVER_ERROR);
            }
        } catch (IOException ignored) {
            // ignore
        }
    }

    private void handleGuess(WebSocketSession session, String username, String fp, String word)
            throws IOException {
        if (!rateLimiter.allow(session.getId(), fp)) {
            sendJson(session, errorPayload("RATE_LIMITED", "猜词过于频繁，请稍后再试"));
            return;
        }

        GuessResult result;
        try {
            result = gameEngine.processGuess(username, fp, word);
        } catch (EmbeddingException e) {
            log.warn("embedding failed for guess: {}", e.getMessage());
            sendJson(session, errorPayload("EMBEDDING_FAILED", "相似度计算失败，请稍后重试"));
            return;
        } catch (IllegalStateException e) {
            String code = e.getMessage() == null ? "ROUND_INACTIVE" : e.getMessage();
            sendJson(session, errorPayload(code, "当前不可猜词"));
            return;
        } catch (IllegalArgumentException e) {
            String code = e.getMessage() == null ? "BAD_WORD" : e.getMessage();
            sendJson(session, errorPayload(code, "词语无效"));
            return;
        }

        sendJson(session, guessResultPayload(result));

        if (result.gameOver()) {
            // Order: personal guess_result → broadcast game_over → round_state + top10 + hints
            broadcast(gameOverPayload(result));
            broadcast(roundStatePayload());
            broadcast(top10Payload());
            broadcast(hintsPayload());
            return;
        }

        if (result.needBroadcastTop10()) {
            broadcast(top10Payload());
        }
        if (result.hintsUpdated()) {
            broadcast(hintsPayload());
        }
    }

    /** Admin start/stop (and similar) — fan out current snapshot; never includes targetWord. */
    @EventListener
    public void onRoundSnapshotChanged(RoundSnapshotChangedEvent event) {
        broadcastRoundSnapshot();
    }

    public void broadcastRoundSnapshot() {
        broadcast(roundStatePayload());
        broadcast(top10Payload());
        broadcast(hintsPayload());
    }

    private void broadcast(Map<String, Object> payload) {
        for (WebSocketSession s : sessions.values()) {
            if (s == null || !s.isOpen()) {
                continue;
            }
            try {
                sendJson(s, payload);
            } catch (IOException e) {
                log.debug("broadcast failed session={}", s.getId(), e);
            }
        }
    }

    private Map<String, Object> roundStatePayload() {
        RoundSnapshot snap = gameEngine.getRoundSnapshot();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "round_state");
        msg.put("status", snap.status());
        msg.put("roundId", snap.roundId());
        msg.put("guessable", snap.guessable());
        return msg;
    }

    private Map<String, Object> top10Payload() {
        List<Map<String, Object>> data = new ArrayList<>();
        for (WordRecord r : gameEngine.getTop10()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("word", r.word());
            row.put("score", r.score());
            row.put("firstUser", r.firstUser());
            data.add(row);
        }
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "top10_update");
        msg.put("data", data);
        return msg;
    }

    private Map<String, Object> hintsPayload() {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "hints_update");
        msg.put("unlockedCount", gameEngine.getHintUnlockLevel());
        msg.put("hints", gameEngine.getUnlockedHints());
        return msg;
    }

    private Map<String, Object> guessResultPayload(GuessResult result) {
        WordRecord r = result.record();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "guess_result");
        msg.put("word", r.word());
        msg.put("score", r.score());
        msg.put("isFirstDiscoverer", result.isFirst());
        msg.put("firstUser", r.firstUser());
        msg.put("alreadyGuessed", result.alreadyGuessed());
        return msg;
    }

    private Map<String, Object> gameOverPayload(GuessResult result) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "game_over");
        msg.put("winner", result.record().firstUser());
        msg.put("word", result.record().word());
        msg.put("next", result.next());
        return msg;
    }

    private Map<String, Object> errorPayload(String code, String message) {
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "error");
        msg.put("code", code);
        msg.put("message", message);
        return msg;
    }

    private void sendJson(WebSocketSession session, Map<String, Object> payload) throws IOException {
        if (session == null || !session.isOpen()) {
            return;
        }
        // Defensive: never leak targetWord on any outbound frame
        if (payload.containsKey("targetWord")) {
            throw new IllegalStateException("targetWord must never be sent on WS");
        }
        String json = objectMapper.writeValueAsString(payload);
        synchronized (session) {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(json));
            }
        }
    }

    private static String textOrNull(JsonNode root, String field) {
        if (root == null || !root.has(field) || root.get(field).isNull()) {
            return null;
        }
        return root.get(field).asText();
    }

    private static Map<String, String> parseQuery(URI uri) {
        Map<String, String> out = new LinkedHashMap<>();
        if (uri == null || uri.getRawQuery() == null || uri.getRawQuery().isBlank()) {
            return out;
        }
        for (String pair : uri.getRawQuery().split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String val = eq >= 0 ? pair.substring(eq + 1) : "";
            out.put(urlDecode(key), urlDecode(val));
        }
        return out;
    }

    private static String urlDecode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    private record NickBinding(String username, long boundAtMs) {}
}
