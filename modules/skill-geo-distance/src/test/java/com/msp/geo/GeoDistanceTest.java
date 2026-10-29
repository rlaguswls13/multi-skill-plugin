package com.msp.geo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoDistanceTest {

    @Nested
    @DisplayName("Coordinate Validation Tests")
    class CoordinateTests {

        @Test
        @DisplayName("Valid coordinates are created successfully")
        void testValidCoordinates() {
            GeoCoordinate coord = new GeoCoordinate(37.5665, 126.9780);
            assertEquals(37.5665, coord.latitude());
            assertEquals(126.9780, coord.longitude());

            // Edge extremes
            assertDoesNotThrow(() -> new GeoCoordinate(-90.0, -180.0));
            assertDoesNotThrow(() -> new GeoCoordinate(90.0, 180.0));
        }

        @Test
        @DisplayName("Out of range latitude or longitude throws IllegalArgumentException")
        void testInvalidCoordinates() {
            assertThrows(IllegalArgumentException.class, () -> new GeoCoordinate(90.1, 0.0));
            assertThrows(IllegalArgumentException.class, () -> new GeoCoordinate(-90.1, 0.0));
            assertThrows(IllegalArgumentException.class, () -> new GeoCoordinate(0.0, 180.1));
            assertThrows(IllegalArgumentException.class, () -> new GeoCoordinate(0.0, -180.1));
            assertThrows(IllegalArgumentException.class, () -> new GeoCoordinate(Double.NaN, 0.0));
        }
    }

    @Nested
    @DisplayName("Distance and Bearing Tests")
    class DistanceTests {

        // Landmark coordinates
        private final GeoCoordinate seoul = new GeoCoordinate(37.5665, 126.9780);
        private final GeoCoordinate busan = new GeoCoordinate(35.1796, 129.0756);
        private final GeoCoordinate london = new GeoCoordinate(51.5074, -0.1278);
        private final GeoCoordinate paris = new GeoCoordinate(48.8566, 2.3522);

        @Test
        @DisplayName("Identical point returns 0 distance and 0 bearing")
        void testIdenticalPoints() {
            assertEquals(0.0, GeoDistance.distanceInMeters(seoul, seoul));
            assertEquals(0.0, GeoDistance.distanceInKm(seoul, seoul));
            assertEquals(0.0, GeoDistance.initialBearing(seoul, seoul));
            assertTrue(GeoDistance.isWithinRadius(seoul, seoul, 0.0));
        }

        @Test
        @DisplayName("Seoul to Busan distance is ~325 km")
        void testSeoulToBusanDistance() {
            double km = GeoDistance.distanceInKm(seoul, busan);
            // Haversine great-circle distance is approx 325.3 km
            assertEquals(325.3, km, 2.0);

            double meters = GeoDistance.distanceInMeters(seoul, busan);
            assertEquals(km * 1000.0, meters, 0.001);

            assertTrue(GeoDistance.isWithinRadius(seoul, busan, 330_000));
            assertFalse(GeoDistance.isWithinRadius(seoul, busan, 320_000));
        }

        @Test
        @DisplayName("London to Paris distance is ~343 km")
        void testLondonToParisDistance() {
            double km = GeoDistance.distanceInKm(london, paris);
            // Great circle distance ~343.5 km
            assertEquals(343.5, km, 2.0);
        }

        @Test
        @DisplayName("Antipode distance equals half Earth circumference (~20,015 km)")
        void testAntipodeDistance() {
            GeoCoordinate p1 = new GeoCoordinate(45.0, 10.0);
            GeoCoordinate p2 = new GeoCoordinate(-45.0, -170.0); // Exact antipode

            double expectedKm = Math.PI * GeoDistance.EARTH_RADIUS_KM; // ~20015.087 km
            double km = GeoDistance.distanceInKm(p1, p2);
            assertEquals(expectedKm, km, 0.5);
        }

        @Test
        @DisplayName("Cardinal direction initial bearings")
        void testCardinalBearings() {
            GeoCoordinate origin = new GeoCoordinate(0.0, 0.0);

            // Due North
            assertEquals(0.0, GeoDistance.initialBearing(origin, new GeoCoordinate(10.0, 0.0)), 0.01);
            // Due East
            assertEquals(90.0, GeoDistance.initialBearing(origin, new GeoCoordinate(0.0, 10.0)), 0.01);
            // Due South
            assertEquals(180.0, GeoDistance.initialBearing(origin, new GeoCoordinate(-10.0, 0.0)), 0.01);
            // Due West
            assertEquals(270.0, GeoDistance.initialBearing(origin, new GeoCoordinate(0.0, -10.0)), 0.01);
        }

        @Test
        @DisplayName("Negative radius throws IllegalArgumentException")
        void testNegativeRadius() {
            assertThrows(IllegalArgumentException.class, () ->
                    GeoDistance.isWithinRadius(seoul, busan, -1.0)
            );
        }
    }

    @Nested
    @DisplayName("Bounding Box Tests")
    class BoundingBoxTests {

        @Test
        @DisplayName("Bounding box around point contains the center and nearby points")
        void testBoundingBoxContainment() {
            GeoCoordinate seoul = new GeoCoordinate(37.5665, 126.9780);
            double radiusMeters = 50_000; // 50 km

            GeoBoundingBox box = GeoBoundingBox.of(seoul, radiusMeters);

            // Center must be contained
            assertTrue(box.contains(seoul));

            // Point 20km away should be contained
            // 20 km North: ~0.18 degrees latitude
            GeoCoordinate nearPoint = new GeoCoordinate(seoul.latitude() + 0.15, seoul.longitude());
            assertTrue(box.contains(nearPoint));

            // Busan is ~325 km away, must NOT be contained
            GeoCoordinate busan = new GeoCoordinate(35.1796, 129.0756);
            assertFalse(box.contains(busan));
        }

        @Test
        @DisplayName("Antimeridian crossing is handled correctly")
        void testAntimeridianCrossing() {
            // Near International Date Line (+179.5 degrees)
            GeoCoordinate nearDateLine = new GeoCoordinate(0.0, 179.5);
            double radiusMeters = 100_000; // 100 km (~0.9 degrees)

            GeoBoundingBox box = GeoBoundingBox.of(nearDateLine, radiusMeters);
            assertTrue(box.crossesAntimeridian());

            // Points on both sides of 180° meridian must be contained
            assertTrue(box.contains(new GeoCoordinate(0.0, 179.9)));
            assertTrue(box.contains(new GeoCoordinate(0.0, -179.9)));

            // Point far away across prime meridian should not be contained
            assertFalse(box.contains(new GeoCoordinate(0.0, 0.0)));
        }

        @Test
        @DisplayName("Polar cap bounding box covers entire longitude range")
        void testPolarCap() {
            GeoCoordinate northPoleNear = new GeoCoordinate(89.5, 0.0);
            double radiusMeters = 100_000; // 100 km reaches the 90.0 degree pole

            GeoBoundingBox box = GeoBoundingBox.of(northPoleNear, radiusMeters);
            assertEquals(90.0, box.maxLat());
            assertEquals(-180.0, box.minLon());
            assertEquals(180.0, box.maxLon());

            // Any longitude at lat 89.6 should be contained
            assertTrue(box.contains(new GeoCoordinate(89.6, -120.0)));
            assertTrue(box.contains(new GeoCoordinate(89.6, 75.0)));
        }
    }
}
