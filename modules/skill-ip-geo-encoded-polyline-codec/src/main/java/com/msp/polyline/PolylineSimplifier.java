package com.msp.polyline;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Iterative (stack-safe) Douglas-Peucker polyline path simplification.
 */
public final class PolylineSimplifier {

    private PolylineSimplifier() {
    }

    /**
     * Simplifies a path using Douglas-Peucker with a perpendicular distance tolerance in meters.
     */
    public static List<GeoPoint> simplify(List<GeoPoint> points, double toleranceMeters) {
        return simplifyInternal(points, toleranceMeters, true);
    }

    /**
     * Simplifies a path using Douglas-Peucker with a perpendicular distance tolerance in decimal degrees.
     */
    public static List<GeoPoint> simplifyByDegrees(List<GeoPoint> points, double toleranceDegrees) {
        return simplifyInternal(points, toleranceDegrees, false);
    }

    private static List<GeoPoint> simplifyInternal(List<GeoPoint> points, double tolerance, boolean useMeters) {
        Objects.requireNonNull(points, "points must not be null");
        if (!Double.isFinite(tolerance) || tolerance < 0.0) {
            throw new IllegalArgumentException("tolerance must be finite and >= 0, got " + tolerance);
        }
        int n = points.size();
        if (n <= 2) {
            return List.copyOf(points);
        }

        boolean[] keep = new boolean[n];
        keep[0] = true;
        keep[n - 1] = true;

        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{0, n - 1});

        while (!stack.isEmpty()) {
            int[] range = stack.pop();
            int start = range[0];
            int end = range[1];

            double maxDist = -1.0;
            int maxIdx = -1;
            GeoPoint a = points.get(start);
            GeoPoint b = points.get(end);

            for (int i = start + 1; i < end; i++) {
                GeoPoint p = points.get(i);
                double dist = useMeters
                        ? GeoDistance.perpendicularDistanceMeters(p, a, b)
                        : GeoDistance.perpendicularDistanceDegrees(p, a, b);
                if (dist > maxDist) {
                    maxDist = dist;
                    maxIdx = i;
                }
            }

            if (maxDist > tolerance && maxIdx != -1) {
                keep[maxIdx] = true;
                if (maxIdx - start > 1) {
                    stack.push(new int[]{start, maxIdx});
                }
                if (end - maxIdx > 1) {
                    stack.push(new int[]{maxIdx, end});
                }
            }
        }

        List<GeoPoint> simplified = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (keep[i]) {
                simplified.add(points.get(i));
            }
        }
        return List.copyOf(simplified);
    }
}
