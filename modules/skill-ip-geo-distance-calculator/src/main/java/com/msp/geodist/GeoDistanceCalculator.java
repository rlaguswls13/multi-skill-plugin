package com.msp.geodist;

import java.util.Objects;

/**
 * Geodesic distance and initial bearing calculator based on Haversine spherical model
 * and Vincenty high-precision WGS-84 ellipsoid algorithm.
 * <p>
 * Zero 3rd-party runtime dependencies. Pure Java 21 standard library.
 */
public final class GeoDistanceCalculator {

    /**
     * IUGG mean Earth radius in meters per IUGG recommendations.
     */
    public static final double EARTH_RADIUS_METERS = 6371008.8;

    /**
     * WGS-84 semi-major axis (equatorial radius) in meters.
     */
    public static final double WGS84_A = 6378137.0;

    /**
     * WGS-84 ellipsoid flattening factor (1 / 298.257223563).
     */
    public static final double WGS84_F = 1.0 / 298.257223563;

    /**
     * WGS-84 semi-minor axis (polar radius) in meters: {@code a * (1 - f)}.
     */
    public static final double WGS84_B = 6356752.314245179;

    private static final double EPSILON = 1e-12;
    private static final int MAX_ITERATIONS = 200;

    private GeoDistanceCalculator() {
        // Utility class
    }

    /**
     * Computes spherical Great-Circle distance between two points in meters using the Haversine formula
     * with Earth radius 6371008.8m.
     *
     * @param p1 start point
     * @param p2 destination point
     * @return distance in meters
     */
    public static double haversineMeters(GeoPoint p1, GeoPoint p2) {
        Objects.requireNonNull(p1, "p1 must not be null");
        Objects.requireNonNull(p2, "p2 must not be null");

        if (p1.equals(p2)) {
            return 0.0;
        }

        double phi1 = Math.toRadians(p1.latitude());
        double phi2 = Math.toRadians(p2.latitude());
        double deltaPhi = Math.toRadians(p2.latitude() - p1.latitude());
        double deltaLambda = Math.toRadians(p2.longitude() - p1.longitude());

        double sinDeltaPhi2 = Math.sin(deltaPhi / 2.0);
        double sinDeltaLambda2 = Math.sin(deltaLambda / 2.0);

        double a = sinDeltaPhi2 * sinDeltaPhi2 +
                   Math.cos(phi1) * Math.cos(phi2) * sinDeltaLambda2 * sinDeltaLambda2;

        // Clamp to avoid numerical errors with Math.sqrt
        a = Math.min(1.0, Math.max(0.0, a));
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));

        return EARTH_RADIUS_METERS * c;
    }

    /**
     * Computes geodesic distance between two points in meters on the WGS-84 ellipsoid
     * using the high-precision iterative Vincenty inverse formula.
     * <p>
     * For antipodal points that fail to converge, gracefully falls back to the Haversine distance.
     *
     * @param p1 start point
     * @param p2 destination point
     * @return distance in meters with millimeter precision
     */
    public static double vincentyMeters(GeoPoint p1, GeoPoint p2) {
        Objects.requireNonNull(p1, "p1 must not be null");
        Objects.requireNonNull(p2, "p2 must not be null");

        if (p1.equals(p2)) {
            return 0.0;
        }

        double phi1 = Math.toRadians(p1.latitude());
        double phi2 = Math.toRadians(p2.latitude());
        double deltaL = Math.toRadians(p2.longitude() - p1.longitude());

        double u1 = Math.atan((1.0 - WGS84_F) * Math.tan(phi1));
        double u2 = Math.atan((1.0 - WGS84_F) * Math.tan(phi2));
        double sinU1 = Math.sin(u1);
        double cosU1 = Math.cos(u1);
        double sinU2 = Math.sin(u2);
        double cosU2 = Math.cos(u2);

        double lambda = deltaL;
        double lambdaPrev;
        double sinSigma;
        double cosSigma;
        double sigma;
        double sinAlpha;
        double cosSqAlpha;
        double cos2SigmaM;
        int iteration = 0;

        do {
            double sinLambda = Math.sin(lambda);
            double cosLambda = Math.cos(lambda);

            double term1 = cosU2 * sinLambda;
            double term2 = cosU1 * sinU2 - sinU1 * cosU2 * cosLambda;
            sinSigma = Math.sqrt(term1 * term1 + term2 * term2);

            if (sinSigma == 0.0) {
                return 0.0; // Coincident or antipodal on pole
            }

            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda;
            sigma = Math.atan2(sinSigma, cosSigma);
            sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma;
            cosSqAlpha = 1.0 - sinAlpha * sinAlpha;

            if (cosSqAlpha == 0.0) {
                cos2SigmaM = 0.0; // Equatorial line
            } else {
                cos2SigmaM = cosSigma - 2.0 * sinU1 * sinU2 / cosSqAlpha;
            }

            double c = WGS84_F / 16.0 * cosSqAlpha * (4.0 + WGS84_F * (4.0 - 3.0 * cosSqAlpha));
            lambdaPrev = lambda;
            lambda = deltaL + (1.0 - c) * WGS84_F * sinAlpha * (
                    sigma + c * sinSigma * (cos2SigmaM + c * cosSigma * (-1.0 + 2.0 * cos2SigmaM * cos2SigmaM))
            );

            iteration++;
        } while (Math.abs(lambda - lambdaPrev) > EPSILON && iteration < MAX_ITERATIONS);

        if (iteration >= MAX_ITERATIONS) {
            // Did not converge (e.g. nearly antipodal points) - fallback to Haversine
            return haversineMeters(p1, p2);
        }

        double uSq = cosSqAlpha * (WGS84_A * WGS84_A - WGS84_B * WGS84_B) / (WGS84_B * WGS84_B);
        double aCoeff = 1.0 + uSq / 16384.0 * (4096.0 + uSq * (-768.0 + uSq * (320.0 - 175.0 * uSq)));
        double bCoeff = uSq / 1024.0 * (256.0 + uSq * (-128.0 + uSq * (74.0 - 47.0 * uSq)));
        double deltaSigma = bCoeff * sinSigma * (
                cos2SigmaM + bCoeff / 4.0 * (
                        cosSigma * (-1.0 + 2.0 * cos2SigmaM * cos2SigmaM) -
                        bCoeff / 6.0 * cos2SigmaM * (-3.0 + 4.0 * sinSigma * sinSigma) * (-3.0 + 4.0 * cos2SigmaM * cos2SigmaM)
                )
        );

        return WGS84_B * aCoeff * (sigma - deltaSigma);
    }

    /**
     * Computes initial forward bearing (azimuth) from p1 towards p2 in degrees [0.0, 360.0).
     *
     * @param p1 start point
     * @param p2 destination point
     * @return initial bearing in degrees (0 = North, 90 = East, 180 = South, 270 = West)
     */
    public static double initialBearingDegrees(GeoPoint p1, GeoPoint p2) {
        Objects.requireNonNull(p1, "p1 must not be null");
        Objects.requireNonNull(p2, "p2 must not be null");

        if (p1.equals(p2)) {
            return 0.0;
        }

        double phi1 = Math.toRadians(p1.latitude());
        double phi2 = Math.toRadians(p2.latitude());
        double deltaLambda = Math.toRadians(p2.longitude() - p1.longitude());

        double y = Math.sin(deltaLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) -
                   Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360.0) % 360.0;
    }

    /**
     * Checks if a point is within a given radius in meters from a center point.
     *
     * @param center       center coordinate
     * @param point        point to check
     * @param radiusMeters search radius in meters
     * @return {@code true} if distance &lt;= radiusMeters
     */
    public static boolean isWithinRadius(GeoPoint center, GeoPoint point, double radiusMeters) {
        if (radiusMeters < 0.0 || Double.isNaN(radiusMeters) || Double.isInfinite(radiusMeters)) {
            throw new IllegalArgumentException("radiusMeters must be non-negative: " + radiusMeters);
        }
        return haversineMeters(center, point) <= radiusMeters;
    }
}
