package com.msp.geofence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class GeofencePolygonTest {

    @Test
    @DisplayName("Haversine calculates accurate distance between coordinates")
    void testHaversineDistance() {
        // Seoul (37.5665, 126.9780) to Busan (35.1796, 129.0756) ~325 km
        GeoPoint seoul = GeoPoint.of(37.5665, 126.9780);
        GeoPoint busan = GeoPoint.of(35.1796, 129.0756);

        double dist = seoul.distanceTo(busan);
        assertTrue(dist > 300_000 && dist < 350_000, "Distance should be approximately 325 km, got: " + dist);
    }

    @Test
    @DisplayName("Ray casting correctly evaluates points inside and outside a convex polygon")
    void testConvexPolygonContainment() {
        // Square: (10, 10), (10, 20), (20, 20), (20, 10)
        GeofencePolygon square = GeofencePolygon.of("sq1", "Test Square", List.of(
                GeoPoint.of(10, 10),
                GeoPoint.of(10, 20),
                GeoPoint.of(20, 20),
                GeoPoint.of(20, 10)
        ));

        // Inside point
        assertTrue(square.contains(GeoPoint.of(15, 15)));

        // Outside point
        assertFalse(square.contains(GeoPoint.of(25, 25)));

        // Outside via bounding box
        assertFalse(square.contains(GeoPoint.of(5, 5)));

        // Point on edge
        assertTrue(square.contains(GeoPoint.of(10, 15)));
    }

    @Test
    @DisplayName("Ray casting correctly handles concave (U-shaped) polygon cavities")
    void testConcavePolygonContainment() {
        // U-shaped concave polygon
        //  (0,0) ---- (0,10)
        //    |          |
        //  (10,0)     (10,10)
        //    |          |
        //  (10,3)-(4,3)-(4,7)-(10,7) -> inner notch at (4, 3) to (4, 7)
        GeofencePolygon uShape = GeofencePolygon.of("u1", "U-Shape", List.of(
                GeoPoint.of(0, 0),
                GeoPoint.of(0, 10),
                GeoPoint.of(10, 10),
                GeoPoint.of(10, 7),
                GeoPoint.of(4, 7),
                GeoPoint.of(4, 3),
                GeoPoint.of(10, 3),
                GeoPoint.of(10, 0)
        ));

        // Point inside the left prong
        assertTrue(uShape.contains(GeoPoint.of(8, 1)));

        // Point inside the base
        assertTrue(uShape.contains(GeoPoint.of(2, 5)));

        // Point inside the hollow cavity of the U (should be outside!)
        assertFalse(uShape.contains(GeoPoint.of(8, 5)), "Hollow cavity of U-shape must NOT be inside");
    }

    @Test
    @DisplayName("Calculates valid centroid and bounding circle")
    void testCentroidAndBoundingRadius() {
        GeofencePolygon triangle = GeofencePolygon.of("tri", List.of(
                GeoPoint.of(0, 0),
                GeoPoint.of(0, 6),
                GeoPoint.of(6, 0)
        ));

        GeoPoint centroid = triangle.centroid();
        assertEquals(2.0, centroid.latitude(), 0.001);
        assertEquals(2.0, centroid.longitude(), 0.001);
        assertTrue(triangle.boundingRadiusMeters() > 0.0);
    }

    @Test
    @DisplayName("Calculates distance to polygon boundary")
    void testDistanceToPolygon() {
        GeofencePolygon box = GeofencePolygon.of("box", List.of(
                GeoPoint.of(0, 0),
                GeoPoint.of(0, 1),
                GeoPoint.of(1, 1),
                GeoPoint.of(1, 0)
        ));

        // Point inside has distance 0.0
        assertEquals(0.0, box.distanceTo(GeoPoint.of(0.5, 0.5)));

        // Point outside has positive distance
        double dist = box.distanceTo(GeoPoint.of(2, 0.5));
        assertTrue(dist > 100_000, "Distance should be ~111 km (1 degree latitude)");
    }

    @Test
    @DisplayName("GeofenceRegistry finds containing and nearest geofences")
    void testGeofenceRegistry() {
        GeofenceRegistry registry = new GeofenceRegistry();

        GeofencePolygon polyA = GeofencePolygon.of("polyA", "Area A", List.of(
                GeoPoint.of(37.50, 127.00),
                GeoPoint.of(37.50, 127.10),
                GeoPoint.of(37.60, 127.10),
                GeoPoint.of(37.60, 127.00)
        ));

        GeofencePolygon polyB = GeofencePolygon.of("polyB", "Area B", List.of(
                GeoPoint.of(37.70, 127.00),
                GeoPoint.of(37.70, 127.10),
                GeoPoint.of(37.80, 127.10),
                GeoPoint.of(37.80, 127.00)
        ));

        registry.register(polyA).register(polyB);
        assertEquals(2, registry.count());

        // Point inside Poly A
        GeoPoint pA = GeoPoint.of(37.55, 127.05);
        List<GeofencePolygon> containingA = registry.findContaining(pA);
        assertEquals(1, containingA.size());
        assertEquals("polyA", containingA.get(0).id());

        // Point closer to Poly B
        GeoPoint pNearB = GeoPoint.of(37.69, 127.05);
        Optional<GeofenceDistanceResult> nearest = registry.findNearest(pNearB);
        assertTrue(nearest.isPresent());
        assertEquals("polyB", nearest.get().polygon().id());
        assertFalse(nearest.get().inside());
    }
}
