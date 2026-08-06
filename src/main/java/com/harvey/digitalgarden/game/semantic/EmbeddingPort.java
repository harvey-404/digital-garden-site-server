package com.harvey.digitalgarden.game.semantic;

public interface EmbeddingPort {
    float[] embed(String text) throws EmbeddingException;
}
