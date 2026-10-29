package com.msp.geo;

import java.util.Objects;

/**
 * Geographical bounding box representing a minimum bounding rectangular region in lat/lon space.
 * <p>
 * Handles antimeridian (180th meridian) boundary wrapping and polar caps.
 *
 * @param minLat minimum latitude (-90.0 to 90.0)
 * @param maxLat maximum latitude (-90.0 to 90.0)
 * @param minLon minimum longitude (-180.0 to 180.0)
 * @param maxLon maximum longitude (-180.0 to 180.0)
 */
public record GeoBoundingBox(double minLat, double maxLat, double minLon, double maxLon) {

    public GeoBoundingBox {
        if (Double.isNaN(minLat) || minLat < -90.0 || minLat > 90.0) {
            throw new IllegalArgumentException("minLat must be between -90.0 and +90.0, got: " + minLat);
        }
        if (Double.isNaN(maxLat) || maxLat < -90.0 || maxLat > 90.0) {
            throw new IllegalArgumentException("maxLat must be between -90.0 and +90.0, got: " + maxLat);
        }
        if (minLat > maxLat) {
            throw new IllegalArgumentException("minLat cannot exceed maxLat: " + minLat + " > " + maxLat);
        }
        if (Double.isNaN(minLon) || minLon < -180.0 || minLon > 180.0) {
            throw new IllegalArgumentException("minLon must be between -180.0 and +180.0, got: " + minLon);
        }
        if (Double.isNaN(maxLon) || maxLon < -180.0 || maxLon > 180.0) {
            throw new IllegalArgumentException("maxLon must be between -180.0 and +180.0, got: " + maxLon);
        }
    }

    /**
     * Calculates the bounding box that encompasses a circle of given radius around a center coordinate.
     *
     * @param center       center coordinate
     * @param radiusMeters radius threshold in meters (must be >= 0)
     * @return bounding box encompassing the search circle
     */
    public static GeoBoundingBox of(GeoCoordinate center, double radiusMeters) {
        Objects.requireNonNull(center, "center coordinate cannot be null");
        if (radiusMeters < 0.0 || Double.isNaN(radiusMeters)) {
            throw new IllegalArgumentException("radiusMeters must be non-negative: " + radiusMeters);
        }

        double radDist = radiusMeters / GeoDistance.EARTH_RADIUS_METERS;
        double radLat = Math.toRadians(center.latitude());
        double radLon = Math.toRadians(center.longitude());

        double minLat = center.latitude() - Math.toDegrees(radDist);
        double maxLat = center.latitude() + Math.toDegrees(radDist);

        if (minLat < -90.0) minLat = -90.0;
        if (maxLat > 90.0) maxLat = 90.0;

        double minLon;
        double maxLon;

        // If the bounding box reaches either pole, all longitudes are covered
        if (minLat <= -90.0 || maxLat >= 90.0) {
            minLon = -180.0;
            maxLon = 180.0;
        } else {
            double cosLat = Math.cos(radLat);
            double sinDist = Math.sin(radDist);

            if (sinDist >= cosLat) {
                // Region wraps all longitudes at high latitudes
                minLon = -180.0;
                maxLon = 180.0;
            } else {
                double deltaLon = Math.toDegrees(Math.asin(sinDist / cosLat));
                minLon = center.longitude() - deltaLon;
                maxLon = center.longitude() + deltaLon;

                if (minLon < -180.0) minLon += 360.0;
                if (maxLon > 180.0) maxLon -= 360.0;
            }
        }

        return new GeoBoundingBox(minLat, maxLat, minLon, maxLon);
    }

    /**
     * Returns true if the bounding box spans across the antimeridian (180th meridian).
     */
    public boolean crossesAntimeridian() {
        return minLon > maxLon;
    }

    /**
     * Checks if the given coordinate is contained within this bounding box.
     */
    public boolean contains(GeoCoordinate coord) {
        Objects.requireNonNull(coord, "coord cannot be null");
        return contains(coord.latitude(), coord.longitude());
    }

    /**
     * Checks if the given latitude and longitude fall within this bounding box.
     */
    public boolean contains(double lat, double lon) {
        if (lat < minLat || lat > maxLat) {
            return false;
        }

        if (crossesAntimeridian()) {
            // e.g. minLon = 170.0, maxLon = -170.0 -> valid if lon >= 170 OR lon <= -170
            return lon >= minLon || lon <= maxLon;
        } else {
            return lon >= minLon && lon <= maxLon;
        }
    }
}
