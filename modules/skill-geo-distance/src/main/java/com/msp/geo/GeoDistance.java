package com.msp.geo;

import java.util.Objects;

/**
 * Great-circle geographic distance and navigation calculator using the Haversine formula.
 */
public final class GeoDistance {

    /** Standard mean radius of the Earth in kilometers (IUGG). */
    public static final double EARTH_RADIUS_KM = 6371.0;

    /** Standard mean radius of the Earth in meters. */
    public static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private GeoDistance() {
    }

    /**
     * Calculates the great-circle distance between two coordinates in meters.
     *
     * @param from starting coordinate
     * @param to   destination coordinate
     * @return distance in meters
     */
    public static double distanceInMeters(GeoCoordinate from, GeoCoordinate to) {
        Objects.requireNonNull(from, "from coordinate cannot be null");
        Objects.requireNonNull(to, "to coordinate cannot be null");

        if (from.equals(to)) {
            return 0.0;
        }

        double lat1Rad = Math.toRadians(from.latitude());
        double lon1Rad = Math.toRadians(from.longitude());
        double lat2Rad = Math.toRadians(to.latitude());
        double lon2Rad = Math.toRadians(to.longitude());

        double deltaLat = lat2Rad - lat1Rad;
        double deltaLon = lon2Rad - lon1Rad;

        double sinHalfDeltaLat = Math.sin(deltaLat / 2.0);
        double sinHalfDeltaLon = Math.sin(deltaLon / 2.0);

        double a = sinHalfDeltaLat * sinHalfDeltaLat
                + Math.cos(lat1Rad) * Math.cos(lat2Rad) * sinHalfDeltaLon * sinHalfDeltaLon;

        // Clamp to avoid numerical floating-point errors outside [0.0, 1.0]
        a = Math.clamp(a, 0.0, 1.0);
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));

        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Calculates the great-circle distance between two coordinates in kilometers.
     *
     * @param from starting coordinate
     * @param to   destination coordinate
     * @return distance in kilometers
     */
    public static double distanceInKm(GeoCoordinate from, GeoCoordinate to) {
        return distanceInMeters(from, to) / 1000.0;
    }

    /**
     * Determines whether two points are within the given radius in meters.
     *
     * @param from         starting coordinate
     * @param to           destination coordinate
     * @param radiusMeters radius threshold in meters (must be >= 0)
     * @return true if distance <= radiusMeters
     */
    public static boolean isWithinRadius(GeoCoordinate from, GeoCoordinate to, double radiusMeters) {
        if (radiusMeters < 0.0 || Double.isNaN(radiusMeters)) {
            throw new IllegalArgumentException("Radius must be non-negative, got: " + radiusMeters);
        }
        return distanceInMeters(from, to) <= radiusMeters;
    }

    /**
     * Computes the initial forward azimuth (bearing) from coordinate 'from' to coordinate 'to' in degrees.
     * Output is normalized to [0.0, 360.0).
     *
     * @param from starting coordinate
     * @param to   destination coordinate
     * @return initial bearing in degrees (0.0 = North, 90.0 = East, 180.0 = South, 270.0 = West)
     */
    public static double initialBearing(GeoCoordinate from, GeoCoordinate to) {
        Objects.requireNonNull(from, "from coordinate cannot be null");
        Objects.requireNonNull(to, "to coordinate cannot be null");

        if (from.equals(to)) {
            return 0.0;
        }

        double lat1Rad = Math.toRadians(from.latitude());
        double lon1Rad = Math.toRadians(from.longitude());
        double lat2Rad = Math.toRadians(to.latitude());
        double lon2Rad = Math.toRadians(to.longitude());

        double deltaLon = lon2Rad - lon1Rad;

        double y = Math.sin(deltaLon) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad)
                - Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(deltaLon);

        double initialBearingRad = Math.atan2(y, x);
        double bearingDeg = Math.toDegrees(initialBearingRad);

        // Normalize to [0.0, 360.0)
        return (bearingDeg + 360.0) % 360.0;
    }
}
