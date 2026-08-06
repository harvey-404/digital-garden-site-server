package com.harvey.digitalgarden.game.semantic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimilarityMapperTest {
    @Test
    void identicalVectors_scoreNearCeil() {
        float[] v = {1f, 0f, 0f};
        double cos = SimilarityMapper.cosine(v, v);
        assertEquals(1.0, cos, 1e-6);
        assertEquals(99.99, SimilarityMapper.toScore(cos), 1e-6);
    }

    @Test
    void orthogonal_nearZero() {
        float[] a = {1f, 0f};
        float[] b = {0f, 1f};
        assertEquals(0.0, SimilarityMapper.toScore(SimilarityMapper.cosine(a, b)), 1e-6);
    }

    @Test
    void negativeCosine_clampedToZero() {
        float[] a = {1f, 0f};
        float[] b = {-1f, 0f};
        assertEquals(0.0, SimilarityMapper.toScore(SimilarityMapper.cosine(a, b)), 1e-6);
    }
}
