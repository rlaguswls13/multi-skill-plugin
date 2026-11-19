package com.msp.quadkey;

/**
 * Geographic bounding box in WGS84 latitude/longitude degrees.
 */
public record GeoBoundingBox(double minLat, double minLon, double maxLat, double maxLon) {

    public GeoBoundingBox {
        if (Double.isNaN(minLat) || Double.isNaN(minLon) || Double.isNaN(maxLat) || Double.isNaN(maxLon)) {
            throw new IllegalArgumentException("Bounding box coordinates must not be NaN");
        }
        if (minLat > maxLat) {
            throw new IllegalArgumentException("minLat (" + minLat + ") must be <= maxLat (" + maxLat + ")");
        }
        if (minLon > maxLon) {
            throw new IllegalArgumentException("minLon (" + minLon + ") must be <= maxLon (" + maxLon + ")");
        }
    }

    public double centerLat() {
        return (minLat + maxLat) / 2.0;
    }

    public double centerLon() {
        return (minLon + maxLon) / 2.0;
    }

    public boolean contains(double latitude, double longitude) {
        return latitude >= minLat && latitude <= maxLat && longitude >= minLon && longitude <= maxLon;
    }
}
