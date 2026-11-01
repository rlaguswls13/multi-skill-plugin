package com.msp.geodist;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GeoDistanceCalculator Unit Tests")
class GeoDistanceCalculatorTest {

    // Benchmark coordinates
    private static final GeoPoint SEOUL = GeoPoint.of(37.5665, 126.9780);
    private static final GeoPoint NEW_YORK = GeoPoint.of(40.7128, -74.0060);
    private static final GeoPoint LONDON = GeoPoint.of(51.5074, -0.1278);
    private static final GeoPoint PARIS = GeoPoint.of(48.8566, 2.3522);

    @Test
    @DisplayName("Identical points return 0 distance and 0 bearing")
    void testIdenticalPoints() {
        assertEquals(0.0, GeoDistanceCalculator.haversineMeters(SEOUL, SEOUL));
        assertEquals(0.0, GeoDistanceCalculator.vincentyMeters(SEOUL, SEOUL));
        assertEquals(0.0, GeoDistanceCalculator.initialBearingDegrees(SEOUL, SEOUL));
    }

    @Test
    @DisplayName("London to Paris benchmark distance")
    void testLondonToParis() {
        double haversine = GeoDistanceCalculator.haversineMeters(LONDON, PARIS);
        double vincenty = GeoDistanceCalculator.vincentyMeters(LONDON, PARIS);

        // London to Paris is ~343.5 km
        assertEquals(343500.0, haversine, 5000.0, "Haversine should be ~343.5km");
        assertEquals(343550.0, vincenty, 3000.0, "Vincenty should be ~343.55km");

        // Both formulas should be very close (<0.5% difference)
        double diff = Math.abs(haversine - vincenty);
        assertTrue(diff < 2000.0, "Spherical vs Ellipsoid difference should be small: " + diff);
    }

    @Test
    @DisplayName("Seoul to New York benchmark distance")
    void testSeoulToNewYork() {
        double haversine = GeoDistanceCalculator.haversineMeters(SEOUL, NEW_YORK);
        double vincenty = GeoDistanceCalculator.vincentyMeters(SEOUL, NEW_YORK);

        // Seoul to NY is ~11,050 km (~1.105e7 meters)
        assertEquals(11_050_000.0, haversine, 150_000.0);
        assertEquals(11_070_000.0, vincenty, 150_000.0);
    }

    @Test
    @DisplayName("Initial bearing calculations along cardinal directions")
    void testCardinalBearings() {
        GeoPoint equator0 = GeoPoint.of(0.0, 0.0);
        GeoPoint north10 = GeoPoint.of(10.0, 0.0);
        GeoPoint south10 = GeoPoint.of(-10.0, 0.0);
        GeoPoint east10 = GeoPoint.of(0.0, 10.0);
        GeoPoint west10 = GeoPoint.of(0.0, -10.0);

        assertEquals(0.0, GeoDistanceCalculator.initialBearingDegrees(equator0, north10), 0.001, "Due North");
        assertEquals(90.0, GeoDistanceCalculator.initialBearingDegrees(equator0, east10), 0.001, "Due East");
        assertEquals(180.0, GeoDistanceCalculator.initialBearingDegrees(equator0, south10), 0.001, "Due South");
        assertEquals(270.0, GeoDistanceCalculator.initialBearingDegrees(equator0, west10), 0.001, "Due West");
    }

    @Test
    @DisplayName("isWithinRadius checks")
    void testIsWithinRadius() {
        GeoPoint p1 = GeoPoint.of(37.5665, 126.9780); // Seoul City Hall
        GeoPoint pNear = GeoPoint.of(37.5670, 126.9785); // ~70 meters away
        GeoPoint pFar = GeoPoint.of(37.5800, 126.9780); // ~1.5 km away

        assertTrue(GeoDistanceCalculator.isWithinRadius(p1, pNear, 200.0));
        assertFalse(GeoDistanceCalculator.isWithinRadius(p1, pFar, 200.0));
        assertTrue(GeoDistanceCalculator.isWithinRadius(p1, pFar, 2000.0));
    }

    @Test
    @DisplayName("Antipodal points fallback and half-circumference distance")
    void testAntipodalPoints() {
        GeoPoint p1 = GeoPoint.of(0.0, 0.0);
        GeoPoint pAntipodal = GeoPoint.of(0.0, 180.0);

        double dist = GeoDistanceCalculator.vincentyMeters(p1, pAntipodal);
        // Half circumference is ~20,015 km (approx 2.0015e7 m)
        assertEquals(20_015_000.0, dist, 200_000.0);
    }

    @Test
    @DisplayName("Coordinate and radius validation")
    void testValidation() {
        // Valid edge coordinates
        assertDoesNotThrow(() -> GeoPoint.of(90.0, 180.0));
        assertDoesNotThrow(() -> GeoPoint.of(-90.0, -180.0));

        // Invalid latitudes
        assertThrows(IllegalArgumentException.class, () -> GeoPoint.of(90.1, 0.0));
        assertThrows(IllegalArgumentException.class, () -> GeoPoint.of(-90.01, 0.0));
        assertThrows(IllegalArgumentException.class, () -> GeoPoint.of(Double.NaN, 0.0));

        // Invalid longitudes
        assertThrows(IllegalArgumentException.class, () -> GeoPoint.of(0.0, 180.1));
        assertThrows(IllegalArgumentException.class, () -> GeoPoint.of(0.0, -180.5));
        assertThrows(IllegalArgumentException.class, () -> GeoPoint.of(0.0, Double.POSITIVE_INFINITY));

        // Invalid radius
        assertThrows(IllegalArgumentException.class, () ->
                GeoDistanceCalculator.isWithinRadius(SEOUL, PARIS, -1.0));
    }
}
