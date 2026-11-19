package com.msp.polyline;

import java.util.List;
import java.util.Objects;

/**
 * Geographic distance and path length utilities on WGS84 spherical Earth.
 */
public final class GeoDistance {

    /** Mean Earth radius in meters (IUGG). */
    public static final double EARTH_RADIUS_METERS = 6_371_008.8;

    private GeoDistance() {
    }

    /**
     * Computes great-circle distance in meters between two points using the Haversine formula.
     */
    public static double haversineMeters(GeoPoint a, GeoPoint b) {
        Objects.requireNonNull(a, "a must not be null");
        Objects.requireNonNull(b, "b must not be null");
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());
        double lat1 = Math.toRadians(a.lat());
        double lat2 = Math.toRadians(b.lat());

        double sinLat = Math.sin(dLat * 0.5);
        double sinLng = Math.sin(dLng * 0.5);
        double h = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLng * sinLng;
        return 2.0 * EARTH_RADIUS_METERS * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    /**
     * Computes total path length in meters across an ordered list of {@link GeoPoint}s.
     */
    public static double pathLengthMeters(List<GeoPoint> points) {
        Objects.requireNonNull(points, "points must not be null");
        if (points.size() < 2) {
            return 0.0;
        }
        double total = 0.0;
        for (int i = 1; i < points.size(); i++) {
            total += haversineMeters(points.get(i - 1), points.get(i));
        }
        return total;
    }

    /**
     * Computes perpendicular distance in meters from {@code point} to segment {@code [start, end]}
     * using a local equirectangular tangent-plane projection.
     */
    public static double perpendicularDistanceMeters(GeoPoint point, GeoPoint start, GeoPoint end) {
        Objects.requireNonNull(point, "point must not be null");
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(end, "end must not be null");

        if (start.equals(end)) {
            return haversineMeters(point, start);
        }

        double refLatRad = Math.toRadians((start.lat() + end.lat()) * 0.5);
        double cosLat = Math.cos(refLatRad);

        double ax = Math.toRadians(start.lng()) * cosLat * EARTH_RADIUS_METERS;
        double ay = Math.toRadians(start.lat()) * EARTH_RADIUS_METERS;
        double bx = Math.toRadians(end.lng()) * cosLat * EARTH_RADIUS_METERS;
        double by = Math.toRadians(end.lat()) * EARTH_RADIUS_METERS;
        double px = Math.toRadians(point.lng()) * cosLat * EARTH_RADIUS_METERS;
        double py = Math.toRadians(point.lat()) * EARTH_RADIUS_METERS;

        return pointToSegmentDistance(px, py, ax, ay, bx, by);
    }

    /**
     * Computes Euclidean perpendicular distance in decimal degrees from {@code point} to segment {@code [start, end]}.
     */
    public static double perpendicularDistanceDegrees(GeoPoint point, GeoPoint start, GeoPoint end) {
        Objects.requireNonNull(point, "point must not be null");
        Objects.requireNonNull(start, "start must not be null");
        Objects.requireNonNull(end, "end must not be null");

        return pointToSegmentDistance(point.lng(), point.lat(), start.lng(), start.lat(), end.lng(), end.lat());
    }

    private static double pointToSegmentDistance(
            double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax;
        double dy = by - ay;
        double lenSq = dx * dx + dy * dy;
        if (lenSq == 0.0) {
            return Math.hypot(px - ax, py - ay);
        }
        double t = ((px - ax) * dx + (py - ay) * dy) / lenSq;
        t = Math.max(0.0, Math.min(1.0, t));
        double projX = ax + t * dx;
        double projY = ay + t * dy;
        return Math.hypot(px - projX, py - projY);
    }
}
