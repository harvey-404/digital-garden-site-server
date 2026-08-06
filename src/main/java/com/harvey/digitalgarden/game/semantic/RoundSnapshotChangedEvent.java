package com.harvey.digitalgarden.game.semantic;

/**
 * Fired after admin start/stop (or similar) mutates engine hot state so WS clients
 * receive {@code round_state} + {@code top10_update} without coupling admin → handler.
 */
public record RoundSnapshotChangedEvent() {}
