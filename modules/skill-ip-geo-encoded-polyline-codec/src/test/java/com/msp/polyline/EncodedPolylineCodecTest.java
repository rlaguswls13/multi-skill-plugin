package com.msp.polyline;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EncodedPolylineCodecTest {

    @Test
    void googleOfficialPrecision5TestVector() {
        List<GeoPoint> points = List.of(
                new GeoPoint(38.5, -120.2),
                new GeoPoint(40.7, -120.95),
                new GeoPoint(43.252, -126.453)
        );
        String expected = "_p~iF~ps|U_ulLnnqC_mqNvxq`@";

        String encoded = EncodedPolylineCodec.encode(points);
        assertEquals(expected, encoded);

        List<GeoPoint> decoded = EncodedPolylineCodec.decode(encoded);
        assertEquals(points.size(), decoded.size());
        for (int i = 0; i < points.size(); i++) {
            assertEquals(points.get(i).lat(), decoded.get(i).lat(), 1e-5);
            assertEquals(points.get(i).lng(), decoded.get(i).lng(), 1e-5);
        }
    }

    @Test
    void precision6RoundTripPreservesSixthDecimal() {
        List<GeoPoint> points = List.of(
                new GeoPoint(37.566535, 126.977969),
                new GeoPoint(37.566536, 126.977971),
                new GeoPoint(-33.868820, 151.209296)
        );
        String encoded = EncodedPolylineCodec.encode(points, PolylinePrecision.PRECISION_6);
        List<GeoPoint> decoded = EncodedPolylineCodec.decode(encoded, PolylinePrecision.PRECISION_6);

        assertEquals(points.size(), decoded.size());
        for (int i = 0; i < points.size(); i++) {
            assertEquals(points.get(i).lat(), decoded.get(i).lat(), 1e-6);
            assertEquals(points.get(i).lng(), decoded.get(i).lng(), 1e-6);
        }
    }

    @Test
    void emptyPathEncodingAndDecoding() {
        assertEquals("", EncodedPolylineCodec.encode(List.of()));
        assertEquals(List.of(), EncodedPolylineCodec.decode(""));
    }

    @Test
    void douglasPeuckerSimplifiesCollinearAndPreservesCorners() {
        // Two straight legs meeting at a sharp corner p2 (0.0045, 0.005),
        // with p1 and p3 having tiny (<1m) wobble off their respective legs
        List<GeoPoint> track = List.of(
                new GeoPoint(0.0, 0.000),
                new GeoPoint(0.002255, 0.0025), // ~0.4m off leg p0->p2 -> removed at 10m tolerance
                new GeoPoint(0.0045, 0.005),    // sharp corner (~500m off p0->p4) -> preserved
                new GeoPoint(0.002253, 0.0075), // ~0.2m off leg p2->p4 -> removed at 10m tolerance
                new GeoPoint(0.0, 0.010)
        );

        List<GeoPoint> simplified = PolylineSimplifier.simplify(track, 10.0);
        assertEquals(3, simplified.size());
        assertEquals(track.get(0), simplified.get(0));
        assertEquals(track.get(2), simplified.get(1));
        assertEquals(track.get(4), simplified.get(2));

        // Degree-based simplification
        List<GeoPoint> byDeg = PolylineSimplifier.simplifyByDegrees(track, 0.0001);
        assertEquals(3, byDeg.size());
    }

    @Test
    void haversineAndPathLengthCalculation() {
        GeoPoint seoul = new GeoPoint(37.5665, 126.9780);
        GeoPoint busan = new GeoPoint(35.1796, 129.0756);
        double dist = GeoDistance.haversineMeters(seoul, busan);
        // Seoul to Busan great-circle distance is ~325 km
        assertTrue(dist > 320_000 && dist < 330_000, "Expected ~325 km, got " + dist);

        double pathLen = GeoDistance.pathLengthMeters(List.of(seoul, busan, seoul));
        assertEquals(dist * 2.0, pathLen, 1e-6);
        assertEquals(0.0, GeoDistance.pathLengthMeters(List.of(seoul)));
    }

    @Test
    void rejectsInvalidCoordinatesAndMalformedPolylines() {
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(91.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(0.0, -181.0));
        assertThrows(IllegalArgumentException.class, () -> new GeoPoint(Double.NaN, 0.0));

        // Missing longitude component
        assertThrows(IllegalArgumentException.class, () -> EncodedPolylineCodec.decode("_p~iF"));
        // Truncated continuation byte
        assertThrows(IllegalArgumentException.class, () -> EncodedPolylineCodec.decode("_p~i"));
        // Invalid character below ASCII 63 ('?')
        assertThrows(IllegalArgumentException.class, () -> EncodedPolylineCodec.decode("!!"));
        assertThrows(IllegalArgumentException.class, () -> PolylineSimplifier.simplify(List.of(), -1.0));
    }
}
