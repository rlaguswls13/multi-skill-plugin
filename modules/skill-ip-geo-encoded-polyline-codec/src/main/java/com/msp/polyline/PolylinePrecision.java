package com.msp.polyline;

/**
 * Coordinate precision factor for the Google Encoded Polyline Algorithm.
 */
public enum PolylinePrecision {
    /** Standard Google Maps precision (5 decimal places, 1e5). */
    PRECISION_5(5, 1e5),
    /** High-precision OSRM / Valhalla precision (6 decimal places, 1e6). */
    PRECISION_6(6, 1e6);

    private final int decimals;
    private final double factor;

    PolylinePrecision(int decimals, double factor) {
        this.decimals = decimals;
        this.factor = factor;
    }

    public int decimals() {
        return decimals;
    }

    public double factor() {
        return factor;
    }
}
