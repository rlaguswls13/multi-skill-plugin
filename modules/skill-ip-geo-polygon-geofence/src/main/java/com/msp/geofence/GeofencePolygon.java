package com.msp.geofence;

import java.util.*;

/**
 * Spatial Geofence Polygon representing a geographical boundary.
 * Supports Ray Casting (Even-Odd) containment check, bounding box optimization,
 * centroid calculation, and distance queries.
 */
public final class GeofencePolygon {

    private static final double EPSILON = 1e-9;

    private final String id;
    private final String name;
    private final List<GeoPoint> vertices;
    private final BoundingBox boundingBox;
    private final GeoPoint centroid;
    private final double boundingRadiusMeters;

    public GeofencePolygon(String id, String name, List<GeoPoint> rawVertices) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.name = name != null ? name : id;
        Objects.requireNonNull(rawVertices, "rawVertices cannot be null");

        // Clean up self-closing vertices
        List<GeoPoint> cleaned = new ArrayList<>(rawVertices);
        if (cleaned.size() > 3 && cleaned.get(0).equals(cleaned.get(cleaned.size() - 1))) {
            cleaned.remove(cleaned.size() - 1);
        }

        if (cleaned.size() < 3) {
            throw new IllegalArgumentException("A polygon must have at least 3 distinct vertices");
        }

        this.vertices = List.copyOf(cleaned);
        this.boundingBox = BoundingBox.fromPoints(this.vertices);
        this.centroid = calculateCentroid(this.vertices);
        this.boundingRadiusMeters = calculateBoundingRadius(this.centroid, this.vertices);
    }

    public static GeofencePolygon of(String id, String name, List<GeoPoint> vertices) {
        return new GeofencePolygon(id, name, vertices);
    }

    public static GeofencePolygon of(String id, List<GeoPoint> vertices) {
        return new GeofencePolygon(id, id, vertices);
    }

    /**
     * Determines whether a point is contained inside this geofence using the Ray Casting Algorithm.
     * Fast O(1) bounding box rejection is applied before the O(V) ray cast.
     */
    public boolean contains(GeoPoint point) {
        if (point == null) {
            return false;
        }

        // 1. O(1) quick rejection
        if (!boundingBox.contains(point)) {
            return false;
        }

        // 2. Check if point lies directly on any boundary edge
        if (isPointOnBoundary(point)) {
            return true;
        }

        // 3. Ray Casting Algorithm (Even-Odd Rule)
        boolean inside = false;
        int n = vertices.size();
        double pLat = point.latitude();
        double pLon = point.longitude();

        for (int i = 0, j = n - 1; i < n; j = i++) {
            GeoPoint v1 = vertices.get(i);
            GeoPoint v2 = vertices.get(j);

            double lat1 = v1.latitude();
            double lon1 = v1.longitude();
            double lat2 = v2.latitude();
            double lon2 = v2.longitude();

            // Check if horizontal ray crosses segment (lat1, lon1) - (lat2, lon2)
            if ((lat1 > pLat) != (lat2 > pLat)) {
                double intersectLon = lon1 + (pLat - lat1) * (lon2 - lon1) / (lat2 - lat1);
                if (pLon < intersectLon) {
                    inside = !inside;
                }
            }
        }

        return inside;
    }

    /**
     * Calculates the minimum distance from a point to the polygon in meters.
     * Returns 0.0 if the point is inside or on the boundary.
     */
    public double distanceTo(GeoPoint point) {
        if (point == null) {
            throw new IllegalArgumentException("point cannot be null");
        }
        if (contains(point)) {
            return 0.0;
        }

        double minDistance = Double.POSITIVE_INFINITY;
        int n = vertices.size();
        for (int i = 0; i < n; i++) {
            GeoPoint v1 = vertices.get(i);
            GeoPoint v2 = vertices.get((i + 1) % n);
            double dist = distanceToSegment(point, v1, v2);
            if (dist < minDistance) {
                minDistance = dist;
            }
        }
        return minDistance;
    }

    private boolean isPointOnBoundary(GeoPoint point) {
        int n = vertices.size();
        for (int i = 0; i < n; i++) {
            GeoPoint v1 = vertices.get(i);
            GeoPoint v2 = vertices.get((i + 1) % n);
            if (isPointOnSegment(point, v1, v2)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPointOnSegment(GeoPoint p, GeoPoint a, GeoPoint b) {
        // Bounding box check for segment
        double minLat = Math.min(a.latitude(), b.latitude()) - EPSILON;
        double maxLat = Math.max(a.latitude(), b.latitude()) + EPSILON;
        double minLon = Math.min(a.longitude(), b.longitude()) - EPSILON;
        double maxLon = Math.max(a.longitude(), b.longitude()) + EPSILON;

        if (p.latitude() < minLat || p.latitude() > maxLat || p.longitude() < minLon || p.longitude() > maxLon) {
            return false;
        }

        // Cross product to check collinearity
        double cross = (p.longitude() - a.longitude()) * (b.latitude() - a.latitude())
                - (p.latitude() - a.latitude()) * (b.longitude() - a.longitude());

        return Math.abs(cross) < 1e-12;
    }

    private static double distanceToSegment(GeoPoint p, GeoPoint a, GeoPoint b) {
        double dLon = b.longitude() - a.longitude();
        double dLat = b.latitude() - a.latitude();
        double lenSq = dLon * dLon + dLat * dLat;

        if (lenSq < EPSILON) {
            return p.distanceTo(a);
        }

        // Projection factor t in [0, 1]
        double t = ((p.longitude() - a.longitude()) * dLon + (p.latitude() - a.latitude()) * dLat) / lenSq;
        t = Math.max(0.0, Math.min(1.0, t));

        GeoPoint projection = new GeoPoint(
                a.latitude() + t * dLat,
                a.longitude() + t * dLon
        );
        return p.distanceTo(projection);
    }

    private static GeoPoint calculateCentroid(List<GeoPoint> pts) {
        int n = pts.size();
        double area = 0.0;
        double cLat = 0.0;
        double cLon = 0.0;

        for (int i = 0; i < n; i++) {
            GeoPoint p1 = pts.get(i);
            GeoPoint p2 = pts.get((i + 1) % n);
            double cross = (p1.longitude() * p2.latitude()) - (p2.longitude() * p1.latitude());
            area += cross;
            cLon += (p1.longitude() + p2.longitude()) * cross;
            cLat += (p1.latitude() + p2.latitude()) * cross;
        }

        area *= 0.5;
        if (Math.abs(area) < 1e-9) {
            // Degenerate polygon: fallback to arithmetic mean
            double sumLat = 0.0;
            double sumLon = 0.0;
            for (GeoPoint p : pts) {
                sumLat += p.latitude();
                sumLon += p.longitude();
            }
            return new GeoPoint(sumLat / n, sumLon / n);
        }

        cLon /= (6.0 * area);
        cLat /= (6.0 * area);

        // Clamping to valid range
        cLat = Math.max(-90.0, Math.min(90.0, cLat));
        cLon = Math.max(-180.0, Math.min(180.0, cLon));

        return new GeoPoint(cLat, cLon);
    }

    private static double calculateBoundingRadius(GeoPoint centroid, List<GeoPoint> vertices) {
        double maxDist = 0.0;
        for (GeoPoint v : vertices) {
            double d = centroid.distanceTo(v);
            if (d > maxDist) {
                maxDist = d;
            }
        }
        return maxDist;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public List<GeoPoint> vertices() {
        return vertices;
    }

    public BoundingBox boundingBox() {
        return boundingBox;
    }

    public GeoPoint centroid() {
        return centroid;
    }

    public double boundingRadiusMeters() {
        return boundingRadiusMeters;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GeofencePolygon that = (GeofencePolygon) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "GeofencePolygon[" + id + " - " + name + ", vertices=" + vertices.size() + "]";
    }
}
