package com.msp.geofence;

/**
 * Result of a nearest geofence search.
 */
public record GeofenceDistanceResult(
        GeofencePolygon polygon,
        double distanceMeters,
        boolean inside
) implements Comparable<GeofenceDistanceResult> {

    @Override
    public int compareTo(GeofenceDistanceResult o) {
        return Double.compare(this.distanceMeters, o.distanceMeters);
    }
}
