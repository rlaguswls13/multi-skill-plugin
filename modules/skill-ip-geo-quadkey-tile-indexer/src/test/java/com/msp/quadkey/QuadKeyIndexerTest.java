package com.msp.quadkey;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuadKeyIndexerTest {

    @Test
    void testLatLonTileQuadKeyRoundTrip() {
        // Seoul City Hall: 37.5665 N, 126.9780 E
        double lat = 37.5665;
        double lon = 126.9780;
        int zoom = 15;

        TileCoord tile = QuadKeyIndexer.latLonToTile(lat, lon, zoom);
        String quadKey = QuadKeyIndexer.tileToQuadKey(tile);
        assertEquals(zoom, quadKey.length());
        assertEquals(quadKey, QuadKeyIndexer.latLonToQuadKey(lat, lon, zoom));

        TileCoord decodedTile = QuadKeyIndexer.quadKeyToTile(quadKey);
        assertEquals(tile, decodedTile);

        GeoBoundingBox bbox = QuadKeyIndexer.quadKeyToBoundingBox(quadKey);
        assertTrue(bbox.contains(lat, lon), "Tile bounding box must contain original point");
    }

    @Test
    void testKnownQuadKeyBitInterleaving() {
        // At zoom 3, tile (x=3, y=5) -> binary x=011, y=101 -> interleaved (y_bit*2 + x_bit):
        // bit 2: y=1, x=0 -> 2
        // bit 1: y=0, x=1 -> 1
        // bit 0: y=1, x=1 -> 3
        // Expected quadKey = "213"
        TileCoord tile = new TileCoord(3, 5, 3);
        String qk = QuadKeyIndexer.tileToQuadKey(tile);
        assertEquals("213", qk);
        assertEquals(tile, QuadKeyIndexer.quadKeyToTile("213"));
    }

    @Test
    void testNeighborsAndHierarchy() {
        String qk = "13210";
        TileCoord tile = QuadKeyIndexer.quadKeyToTile(qk);

        // 8 neighbors for interior tile
        List<TileCoord> neighbors = QuadKeyIndexer.neighbors(tile);
        assertEquals(8, neighbors.size());
        List<String> neighborKeys = QuadKeyIndexer.neighborQuadKeys(qk);
        assertEquals(8, neighborKeys.size());
        assertFalse(neighborKeys.contains(qk));

        // Parent (zoom 4)
        assertEquals("1321", QuadKeyIndexer.parentQuadKey(qk));
        assertEquals(QuadKeyIndexer.quadKeyToTile("1321"), QuadKeyIndexer.parent(tile));

        // 4 Children (zoom 6)
        List<String> childrenKeys = QuadKeyIndexer.childrenQuadKeys(qk);
        assertEquals(List.of("132100", "132101", "132102", "132103"), childrenKeys);
        List<TileCoord> childrenTiles = QuadKeyIndexer.children(tile);
        assertEquals(4, childrenTiles.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(childrenKeys.get(i), childrenTiles.get(i).toQuadKey());
        }
    }

    @Test
    void testPolarAndAntimeridianNeighborWrapping() {
        // North-west corner tile (x=0, y=0, zoom=2): Y=0 cannot go north (-1), X=0 wraps around to X=3
        TileCoord nwCorner = new TileCoord(0, 0, 2);
        List<TileCoord> neighbors = QuadKeyIndexer.neighbors(nwCorner);
        // Only y=0 (2 neighbors: x=3, x=1) and y=1 (3 neighbors: x=3, x=0, x=1) -> 5 neighbors
        assertEquals(5, neighbors.size());
        assertTrue(neighbors.contains(new TileCoord(3, 0, 2)));
    }

    @Test
    void testBoundingBoxPrefixRangeCoalescing() {
        // Bounding box of parent quadkey "12" shrunk slightly inward so all 4 children ("120","121","122","123")
        // at zoom 3 are covered and coalesced back into a single prefix range "12"
        GeoBoundingBox parentBox = QuadKeyIndexer.quadKeyToBoundingBox("12");
        GeoBoundingBox innerBox = new GeoBoundingBox(
                parentBox.minLat() + 0.01,
                parentBox.minLon() + 0.01,
                parentBox.maxLat() - 0.01,
                parentBox.maxLon() - 0.01
        );

        List<QuadKeyPrefixRange> ranges = QuadKeyIndexer.coverBoundingBoxWithPrefixRanges(innerBox, 3, 64);
        assertEquals(1, ranges.size());
        QuadKeyPrefixRange range = ranges.get(0);
        assertEquals("12", range.prefix());
        assertEquals("120", range.minQuadKeyAtZoom());
        assertEquals("123", range.maxQuadKeyAtZoom());
        assertTrue(range.matches("1221"));
        assertFalse(range.matches("1300"));
    }

    @Test
    void testBoundaryValidation() {
        assertThrows(IllegalArgumentException.class, () -> new TileCoord(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TileCoord(0, 0, 24));
        assertThrows(IllegalArgumentException.class, () -> new TileCoord(4, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> QuadKeyIndexer.quadKeyToTile("124"));
        assertThrows(IllegalArgumentException.class, () -> QuadKeyIndexer.parentQuadKey("0"));
        assertThrows(IllegalArgumentException.class, () -> QuadKeyIndexer.childrenQuadKeys("0".repeat(23)));
    }
}
