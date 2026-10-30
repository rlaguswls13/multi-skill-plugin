package com.msp.geohash;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GeoHashCodec Tests")
class GeoHashCodecTest {

    @Nested
    @DisplayName("Encoding Tests")
    class EncodingTests {

        @Test
        @DisplayName("Encodes equator/prime-meridian intersection correctly")
        void encodeOrigin() {
            String hash = GeoHashCodec.encode(0.0, 0.0, 5);
            assertEquals(5, hash.length());
            assertTrue(hash.startsWith("s0000"));
        }

        @ParameterizedTest
        @CsvSource({
                "37.5665, 126.9780, 6, wydm9q",     // Seoul City Hall
                "51.5074, -0.1278, 6, gcpvj0",     // London Trafalgar
                "37.7749, -122.4194, 6, 9q8yyk",   // San Francisco
                "-33.8688, 151.2093, 6, r3gx2f"    // Sydney
        })
        @DisplayName("Encodes famous world landmarks matching reference geohashes")
        void encodeLandmarks(double lat, double lng, int precision, String expectedPrefix) {
            String hash = GeoHashCodec.encode(lat, lng, precision);
            assertEquals(expectedPrefix, hash);
        }

        @Test
        @DisplayName("Precision bounds are respected")
        void precisionBounds() {
            assertThrows(IllegalArgumentException.class, () -> GeoHashCodec.encode(0, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> GeoHashCodec.encode(0, 0, 13));
            assertEquals(1, GeoHashCodec.encode(0, 0, 1).length());
            assertEquals(12, GeoHashCodec.encode(0, 0, 12).length());
        }

        @Test
        @DisplayName("Out of range latitude and longitude throw IllegalArgumentException")
        void outOfRangeCoordinates() {
            assertThrows(IllegalArgumentException.class, () -> GeoHashCodec.encode(90.1, 0, 5));
            assertThrows(IllegalArgumentException.class, () -> GeoHashCodec.encode(-90.1, 0, 5));
            assertThrows(IllegalArgumentException.class, () -> GeoHashCodec.encode(0, 180.1, 5));
            assertThrows(IllegalArgumentException.class, () -> GeoHashCodec.encode(0, -180.1, 5));
        }
    }

    @Nested
    @DisplayName("Decoding Tests")
    class DecodingTests {

        @Test
        @DisplayName("Decodes geohash back to bounding box containing original coordinate")
        void decodeRoundTrip() {
            double originalLat = 37.566535;
            double originalLng = 126.977969;
            String hash = GeoHashCodec.encode(originalLat, originalLng, 9);

            BoundingBox box = GeoHashCodec.decode(hash);
            assertTrue(box.contains(originalLat, originalLng), "Bounding box must contain original coordinates");
            assertEquals(originalLat, box.centerLat(), 0.0001);
            assertEquals(originalLng, box.centerLng(), 0.0001);
        }

        @Test
        @DisplayName("Decodes uppercase and lowercase identically")
        void caseInsensitive() {
            BoundingBox boxLower = GeoHashCodec.decode("wydm64");
            BoundingBox boxUpper = GeoHashCodec.decode("WYDM64");
            assertEquals(boxLower.centerLat(), boxUpper.centerLat(), 1e-9);
            assertEquals(boxLower.centerLng(), boxUpper.centerLng(), 1e-9);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   ", "abc!", "wydm64a", "wydm64i", "wydm64l", "wydm64o"})
        @DisplayName("Illegal characters ('a', 'i', 'l', 'o', etc.) throw IllegalArgumentException")
        void invalidCharacters(String invalid) {
            assertThrows(IllegalArgumentException.class, () -> GeoHashCodec.decode(invalid));
        }

        @Test
        void nullGeohashThrows() {
            assertThrows(NullPointerException.class, () -> GeoHashCodec.decode(null));
        }
    }

    @Nested
    @DisplayName("Neighbors Tests")
    class NeighborsTests {

        @Test
        @DisplayName("Calculates 8 adjacent neighbors correctly")
        void neighborsCalculation() {
            String seoul = "wydm64";
            Neighbors neighbors = GeoHashCodec.getNeighbors(seoul);

            assertNotNull(neighbors.north());
            assertNotNull(neighbors.northEast());
            assertNotNull(neighbors.east());
            assertNotNull(neighbors.southEast());
            assertNotNull(neighbors.south());
            assertNotNull(neighbors.southWest());
            assertNotNull(neighbors.west());
            assertNotNull(neighbors.northWest());

            List<String> list = neighbors.toList();
            assertEquals(8, list.size());
            // None should equal the center cell
            assertFalse(list.contains(seoul));
            // All neighbors should have length equal to precision
            assertTrue(list.stream().allMatch(h -> h.length() == seoul.length()));

            // Center decoded box
            BoundingBox centerBox = GeoHashCodec.decode(seoul);
            BoundingBox northBox = GeoHashCodec.decode(neighbors.north());
            assertTrue(northBox.centerLat() > centerBox.centerLat(), "North cell centerLat should be higher");
            assertEquals(centerBox.centerLng(), northBox.centerLng(), 1e-4);

            BoundingBox eastBox = GeoHashCodec.decode(neighbors.east());
            assertTrue(eastBox.centerLng() > centerBox.centerLng(), "East cell centerLng should be higher");
            assertEquals(centerBox.centerLat(), eastBox.centerLat(), 1e-4);
        }

        @Test
        @DisplayName("Neighbors wrapping around antimeridian (180 degrees longitude)")
        void antimeridianWrapping() {
            // Geohash near longitude 179.9
            String hash = GeoHashCodec.encode(0.0, 179.99, 5);
            Neighbors neighbors = GeoHashCodec.getNeighbors(hash);
            assertNotNull(neighbors);
            assertEquals(8, neighbors.toList().size());
        }
    }
}
