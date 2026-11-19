package com.msp.countmin;

/**
 * Configuration parameters for a Count-Min Sketch.
 *
 * @param width   number of columns in each hash row (w = ceil(e / epsilon))
 * @param depth   number of hash rows (d = ceil(ln(1 / delta)))
 * @param epsilon relative error tolerance
 * @param delta   error probability bound
 */
public record CountMinConfig(int width, int depth, double epsilon, double delta) {

    public CountMinConfig {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive: " + width);
        }
        if (depth <= 0) {
            throw new IllegalArgumentException("depth must be positive: " + depth);
        }
        if (epsilon <= 0.0 || epsilon >= 1.0) {
            throw new IllegalArgumentException("epsilon must be in (0, 1): " + epsilon);
        }
        if (delta <= 0.0 || delta >= 1.0) {
            throw new IllegalArgumentException("delta must be in (0, 1): " + delta);
        }
    }

    public static CountMinConfig of(double epsilon, double delta) {
        int w = (int) Math.ceil(Math.E / epsilon);
        int d = (int) Math.ceil(Math.log(1.0 / delta));
        return new CountMinConfig(Math.max(w, 1), Math.max(d, 1), epsilon, delta);
    }

    public static CountMinConfig ofDimensions(int width, int depth) {
        double eps = Math.E / width;
        double del = Math.exp(-depth);
        return new CountMinConfig(width, depth, eps, del);
    }
}
