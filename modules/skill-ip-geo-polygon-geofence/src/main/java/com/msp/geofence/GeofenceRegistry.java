package com.msp.geofence;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe multi-geofence manager for spatial lookup and nearest-polygon queries.
 */
public final class GeofenceRegistry {

    private final Map<String, GeofencePolygon> registry = new ConcurrentHashMap<>();

    public GeofenceRegistry register(GeofencePolygon polygon) {
        Objects.requireNonNull(polygon, "polygon cannot be null");
        registry.put(polygon.id(), polygon);
        return this;
    }

    public boolean remove(String id) {
        if (id == null) return false;
        return registry.remove(id) != null;
    }

    public Optional<GeofencePolygon> get(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(registry.get(id));
    }

    public int count() {
        return registry.size();
    }

    public void clear() {
        registry.clear();
    }

    /**
     * Finds all registered geofence polygons that contain the given point.
     */
    public List<GeofencePolygon> findContaining(GeoPoint point) {
        if (point == null) {
            return Collections.emptyList();
        }

        List<GeofencePolygon> containing = new ArrayList<>();
        for (GeofencePolygon polygon : registry.values()) {
            if (polygon.contains(point)) {
                containing.add(polygon);
            }
        }
        return Collections.unmodifiableList(containing);
    }

    /**
     * Finds the nearest geofence to the given point, returning the polygon and distance in meters.
     */
    public Optional<GeofenceDistanceResult> findNearest(GeoPoint point) {
        if (point == null || registry.isEmpty()) {
            return Optional.empty();
        }

        GeofencePolygon nearest = null;
        double minDistance = Double.POSITIVE_INFINITY;
        boolean isInside = false;

        for (GeofencePolygon poly : registry.values()) {
            double dist = poly.distanceTo(point);
            if (dist < minDistance) {
                minDistance = dist;
                nearest = poly;
                isInside = (dist == 0.0);
                if (isInside) {
                    // Inside nearest is 0m
                    break;
                }
            }
        }

        if (nearest == null) {
            return Optional.empty();
        }

        return Optional.of(new GeofenceDistanceResult(nearest, minDistance, isInside));
    }
}
