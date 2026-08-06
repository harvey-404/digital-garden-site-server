package com.harvey.digitalgarden.game.semantic;

public final class SimilarityMapper {
    private SimilarityMapper() {}

    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            throw new IllegalArgumentException("vector length mismatch");
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /** Non-exact scores only; caller assigns 100.0 for exact target match. */
    public static double toScore(double cosine) {
        double c = Math.max(0.0, Math.min(1.0, cosine));
        return Math.round(c * 9999.0) / 100.0; // 0.00 .. 99.99
    }
}
