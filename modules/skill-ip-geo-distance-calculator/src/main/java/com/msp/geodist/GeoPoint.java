package com.msp.geodist;

/**
 * Represents a geographical coordinate point (latitude and longitude) on Earth.
 *
 * @param latitude  Latitude in degrees, strictly between -90.0 and +90.0 inclusive.
 * @param longitude Longitude in degrees, strictly between -180.0 and +180.0 inclusive.
 */
public record GeoPoint(double latitude, double longitude) {

    public GeoPoint {
        if (Double.isNaN(latitude) || Double.isInfinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be a valid number between -90.0 and +90.0: " + latitude);
        }
        if (Double.isNaN(longitude) || Double.isInfinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be a valid number between -180.0 and +180.0: " + longitude);
        }
    }

    /**
     * Factory method creating a GeoPoint.
     */
    public static GeoPoint of(double latitude, double longitude) {
        return new GeoPoint(latitude, longitude);
    }
}
