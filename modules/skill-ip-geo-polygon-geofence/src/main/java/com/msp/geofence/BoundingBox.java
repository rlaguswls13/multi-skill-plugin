package com.msp.geofence;

import java.util.List;

/**
 * Minimum bounding box envelope for fast spatial filtering.
 */
public record BoundingBox(
        double minLatitude,
        double maxLatitude,
        double minLongitude,
        double maxLongitude
) {
    public static BoundingBox fromPoints(List<GeoPoint> points) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("Points list cannot be null or empty");
        }

        double minLat = Double.POSITIVE_INFINITY;
        double maxLat = Double.NEGATIVE_INFINITY;
        double minLon = Double.POSITIVE_INFINITY;
        double maxLon = Double.NEGATIVE_INFINITY;

        for (GeoPoint p : points) {
            if (p.latitude() < minLat) minLat = p.latitude();
            if (p.latitude() > maxLat) maxLat = p.latitude();
            if (p.longitude() < minLon) minLon = p.longitude();
            if (p.longitude() > maxLon) maxLon = p.longitude();
        }

        return new BoundingBox(minLat, maxLat, minLon, maxLon);
    }

    /**
     * Checks if a point lies within this bounding box (inclusive).
     */
    public boolean contains(GeoPoint point) {
        if (point == null) return false;
        return point.latitude() >= minLatitude && point.latitude() <= maxLatitude
                && point.longitude() >= minLongitude && point.longitude() <= maxLongitude;
    }
}
