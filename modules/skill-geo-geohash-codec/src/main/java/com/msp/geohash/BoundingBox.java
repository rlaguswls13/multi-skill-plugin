package com.msp.geohash;

import java.io.Serializable;

/**
 * Geographical bounding box representing the spatial boundary and centroid of a GeoHash cell.
 *
 * @param minLat    Minimum latitude (southern boundary) [-90.0, 90.0]
 * @param maxLat    Maximum latitude (northern boundary) [-90.0, 90.0]
 * @param minLng    Minimum longitude (western boundary) [-180.0, 180.0]
 * @param maxLng    Maximum longitude (eastern boundary) [-180.0, 180.0]
 * @param centerLat Centroid latitude
 * @param centerLng Centroid longitude
 */
public record BoundingBox(
        double minLat,
        double maxLat,
        double minLng,
        double maxLng,
        double centerLat,
        double centerLng
) implements Serializable {

    public BoundingBox {
        if (minLat > maxLat) {
            throw new IllegalArgumentException("minLat (" + minLat + ") cannot be greater than maxLat (" + maxLat + ")");
        }
        if (minLng > maxLng) {
            throw new IllegalArgumentException("minLng (" + minLng + ") cannot be greater than maxLng (" + maxLng + ")");
        }
    }

    /**
     * Latitude span (height) in degrees.
     */
    public double latitudeSpan() {
        return maxLat - minLat;
    }

    /**
     * Longitude span (width) in degrees.
     */
    public double longitudeSpan() {
        return maxLng - minLng;
    }

    /**
     * Checks if given coordinate falls inside this bounding box.
     */
    public boolean contains(double lat, double lng) {
        return lat >= minLat && lat <= maxLat && lng >= minLng && lng <= maxLng;
    }
}
