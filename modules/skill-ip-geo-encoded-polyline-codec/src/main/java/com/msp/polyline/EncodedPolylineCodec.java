package com.msp.polyline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Thread-safe bidirectional codec for the Google Encoded Polyline Algorithm (precision 5 and 6).
 */
public final class EncodedPolylineCodec {

    private EncodedPolylineCodec() {
    }

    /**
     * Encodes a list of {@link GeoPoint}s using standard precision 5 ({@code 1e5}).
     */
    public static String encode(List<GeoPoint> points) {
        return encode(points, PolylinePrecision.PRECISION_5);
    }

    /**
     * Encodes a list of {@link GeoPoint}s using the specified {@link PolylinePrecision}.
     */
    public static String encode(List<GeoPoint> points, PolylinePrecision precision) {
        Objects.requireNonNull(points, "points must not be null");
        Objects.requireNonNull(precision, "precision must not be null");
        if (points.isEmpty()) {
            return "";
        }

        double factor = precision.factor();
        StringBuilder out = new StringBuilder(points.size() * 6);
        long prevLat = 0;
        long prevLng = 0;

        for (GeoPoint p : points) {
            Objects.requireNonNull(p, "point element must not be null");
            long lat = Math.round(p.lat() * factor);
            long lng = Math.round(p.lng() * factor);
            encodeSigned(lat - prevLat, out);
            encodeSigned(lng - prevLng, out);
            prevLat = lat;
            prevLng = lng;
        }
        return out.toString();
    }

    /**
     * Decodes a polyline string using standard precision 5 ({@code 1e5}).
     */
    public static List<GeoPoint> decode(String encoded) {
        return decode(encoded, PolylinePrecision.PRECISION_5);
    }

    /**
     * Decodes a polyline string using the specified {@link PolylinePrecision}.
     */
    public static List<GeoPoint> decode(String encoded, PolylinePrecision precision) {
        Objects.requireNonNull(encoded, "encoded must not be null");
        Objects.requireNonNull(precision, "precision must not be null");
        if (encoded.isEmpty()) {
            return List.of();
        }

        double factor = precision.factor();
        List<GeoPoint> result = new ArrayList<>();
        int[] index = {0};
        int len = encoded.length();
        long lat = 0;
        long lng = 0;

        while (index[0] < len) {
            lat += decodeSigned(encoded, index);
            if (index[0] >= len) {
                throw new IllegalArgumentException("Truncated polyline: missing longitude component");
            }
            lng += decodeSigned(encoded, index);
            result.add(new GeoPoint(lat / factor, lng / factor));
        }
        return List.copyOf(result);
    }

    private static void encodeSigned(long delta, StringBuilder out) {
        long shifted = delta << 1;
        if (delta < 0) {
            shifted = ~shifted;
        }
        while (shifted >= 0x20) {
            out.append((char) ((0x20 | (shifted & 0x1F)) + 63));
            shifted >>>= 5;
        }
        out.append((char) (shifted + 63));
    }

    private static long decodeSigned(String encoded, int[] index) {
        long result = 0;
        int shift = 0;
        int len = encoded.length();
        while (true) {
            if (index[0] >= len) {
                throw new IllegalArgumentException("Truncated polyline encoding at index " + index[0]);
            }
            int b = encoded.charAt(index[0]++) - 63;
            if (b < 0 || b > 0x3F) {
                throw new IllegalArgumentException(
                        "Invalid polyline character at index " + (index[0] - 1));
            }
            if (shift > 60) {
                throw new IllegalArgumentException("Polyline value overflow");
            }
            result |= (long) (b & 0x1F) << shift;
            shift += 5;
            if (b < 0x20) {
                break;
            }
        }
        return (result & 1L) != 0L ? ~(result >>> 1) : (result >>> 1);
    }
}
