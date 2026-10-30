package com.msp.geohash;

import java.util.Arrays;
import java.util.Objects;

/**
 * High-performance, zero-dependency GeoHash encoder, decoder, and spatial neighbor calculator.
 * <p>
 * Implements the standard GeoHash Base32 spatial indexing specification.
 */
public final class GeoHashCodec {

    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";
    private static final int[] DECODE_MAP = new int[128];

    static {
        Arrays.fill(DECODE_MAP, -1);
        for (int i = 0; i < BASE32.length(); i++) {
            char c = BASE32.charAt(i);
            DECODE_MAP[c] = i;
            DECODE_MAP[Character.toUpperCase(c)] = i;
        }
    }

    private GeoHashCodec() {
        // Utility class
    }

    /**
     * Encodes latitude and longitude coordinates into a GeoHash string of given precision.
     *
     * @param latitude  Latitude in degrees [-90.0, 90.0]
     * @param longitude Longitude in degrees [-180.0, 180.0]
     * @param precision Number of characters in resulting GeoHash (1 to 12)
     * @return GeoHash string in lowercase Base32
     */
    public static String encode(double latitude, double longitude, int precision) {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90.0 and 90.0, got: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180.0 and 180.0, got: " + longitude);
        }
        if (precision < 1 || precision > 12) {
            throw new IllegalArgumentException("Precision must be between 1 and 12, got: " + precision);
        }

        double minLat = -90.0, maxLat = 90.0;
        double minLng = -180.0, maxLng = 180.0;
        StringBuilder sb = new StringBuilder(precision);

        int bit = 0;
        int ch = 0;
        boolean isLng = true;

        while (sb.length() < precision) {
            if (isLng) {
                double mid = (minLng + maxLng) / 2.0;
                if (longitude >= mid) {
                    ch |= (1 << (4 - bit));
                    minLng = mid;
                } else {
                    maxLng = mid;
                }
            } else {
                double mid = (minLat + maxLat) / 2.0;
                if (latitude >= mid) {
                    ch |= (1 << (4 - bit));
                    minLat = mid;
                } else {
                    maxLat = mid;
                }
            }

            isLng = !isLng;

            if (bit < 4) {
                bit++;
            } else {
                sb.append(BASE32.charAt(ch));
                bit = 0;
                ch = 0;
            }
        }

        return sb.toString();
    }

    /**
     * Decodes a GeoHash string into a {@link BoundingBox} containing spatial boundaries and centroid.
     *
     * @param geohash Lowercase or uppercase valid GeoHash string
     * @return BoundingBox with coordinates
     */
    public static BoundingBox decode(String geohash) {
        Objects.requireNonNull(geohash, "geohash must not be null");
        String trimmed = geohash.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("geohash must not be empty");
        }

        double minLat = -90.0, maxLat = 90.0;
        double minLng = -180.0, maxLng = 180.0;
        boolean isLng = true;

        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c >= DECODE_MAP.length || DECODE_MAP[c] == -1) {
                throw new IllegalArgumentException("Invalid GeoHash character '" + c + "' at position " + i);
            }
            int val = DECODE_MAP[c];

            for (int bit = 4; bit >= 0; bit--) {
                int mask = 1 << bit;
                if (isLng) {
                    double mid = (minLng + maxLng) / 2.0;
                    if ((val & mask) != 0) {
                        minLng = mid;
                    } else {
                        maxLng = mid;
                    }
                } else {
                    double mid = (minLat + maxLat) / 2.0;
                    if ((val & mask) != 0) {
                        minLat = mid;
                    } else {
                        maxLat = mid;
                    }
                }
                isLng = !isLng;
            }
        }

        double centerLat = (minLat + maxLat) / 2.0;
        double centerLng = (minLng + maxLng) / 2.0;
        return new BoundingBox(minLat, maxLat, minLng, maxLng, centerLat, centerLng);
    }

    /**
     * Calculates the 8 surrounding adjacent neighbor hashes (N, NE, E, SE, S, SW, W, NW)
     * at the same precision as the given GeoHash.
     *
     * @param geohash GeoHash string
     * @return {@link Neighbors} containing the 8 adjacent neighbor GeoHashes
     */
    public static Neighbors getNeighbors(String geohash) {
        BoundingBox box = decode(geohash);
        int precision = geohash.trim().length();
        double latSpan = box.latitudeSpan();
        double lngSpan = box.longitudeSpan();

        String north = encodeNormalized(box.centerLat() + latSpan, box.centerLng(), precision);
        String south = encodeNormalized(box.centerLat() - latSpan, box.centerLng(), precision);
        String east = encodeNormalized(box.centerLat(), box.centerLng() + lngSpan, precision);
        String west = encodeNormalized(box.centerLat(), box.centerLng() - lngSpan, precision);
        String northEast = encodeNormalized(box.centerLat() + latSpan, box.centerLng() + lngSpan, precision);
        String northWest = encodeNormalized(box.centerLat() + latSpan, box.centerLng() - lngSpan, precision);
        String southEast = encodeNormalized(box.centerLat() - latSpan, box.centerLng() + lngSpan, precision);
        String southWest = encodeNormalized(box.centerLat() - latSpan, box.centerLng() - lngSpan, precision);

        return new Neighbors(north, northEast, east, southEast, south, southWest, west, northWest);
    }

    private static String encodeNormalized(double lat, double lng, int precision) {
        // Clamp latitude to [-90.0, 90.0]
        if (lat > 90.0) {
            lat = 90.0;
        } else if (lat < -90.0) {
            lat = -90.0;
        }

        // Wrap longitude to [-180.0, 180.0]
        while (lng > 180.0) {
            lng -= 360.0;
        }
        while (lng < -180.0) {
            lng += 360.0;
        }

        return encode(lat, lng, precision);
    }
}
