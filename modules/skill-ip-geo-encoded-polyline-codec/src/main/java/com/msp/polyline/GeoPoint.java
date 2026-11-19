package com.msp.polyline;

/**
 * Immutable WGS84 geographic coordinate {@code (lat, lng)} in decimal degrees.
 */
public record GeoPoint(double lat, double lng) {
    public GeoPoint {
        if (!Double.isFinite(lat) || lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException("Latitude must be in [-90, 90], got " + lat);
        }
        if (!Double.isFinite(lng) || lng < -180.0 || lng > 180.0) {
            throw new IllegalArgumentException("Longitude must be in [-180, 180], got " + lng);
        }
    }
}
