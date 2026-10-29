package com.msp.geo;

/**
 * Immutable WGS84 geographical coordinate (latitude and longitude).
 *
 * @param latitude  latitude in degrees between -90.0 and +90.0
 * @param longitude longitude in degrees between -180.0 and +180.0
 */
public record GeoCoordinate(double latitude, double longitude) {

    public GeoCoordinate {
        if (Double.isNaN(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90.0 and +90.0 degrees, got: " + latitude);
        }
        if (Double.isNaN(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180.0 and +180.0 degrees, got: " + longitude);
        }
    }

    /**
     * Factory method for convenience.
     */
    public static GeoCoordinate of(double latitude, double longitude) {
        return new GeoCoordinate(latitude, longitude);
    }
}
