package com.msp.geofence;

/**
 * Geographic coordinate representing a point on Earth (WGS84).
 */
public record GeoPoint(double latitude, double longitude) {

    public static final double EARTH_RADIUS_METERS = 6_371_000.0;

    public GeoPoint {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90, got: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180, got: " + longitude);
        }
    }

    public static GeoPoint of(double latitude, double longitude) {
        return new GeoPoint(latitude, longitude);
    }

    /**
     * Calculates the great-circle distance to another GeoPoint in meters using the Haversine formula.
     */
    public double distanceTo(GeoPoint other) {
        if (other == null) {
            throw new IllegalArgumentException("other cannot be null");
        }

        double lat1Rad = Math.toRadians(this.latitude);
        double lat2Rad = Math.toRadians(other.latitude);
        double deltaLat = Math.toRadians(other.latitude - this.latitude);
        double deltaLon = Math.toRadians(other.longitude - this.longitude);

        double a = Math.sin(deltaLat / 2.0) * Math.sin(deltaLat / 2.0)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLon / 2.0) * Math.sin(deltaLon / 2.0);

        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_METERS * c;
    }
}
