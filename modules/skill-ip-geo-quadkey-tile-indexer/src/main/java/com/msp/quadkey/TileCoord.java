package com.msp.quadkey;

/**
 * Represents a Web Mercator tile coordinate at a given zoom level (1..23).
 */
public record TileCoord(int x, int y, int zoom) {

    public static final int MIN_ZOOM = 1;
    public static final int MAX_ZOOM = 23;

    public TileCoord {
        if (zoom < MIN_ZOOM || zoom > MAX_ZOOM) {
            throw new IllegalArgumentException("Zoom level must be in [1..23], got: " + zoom);
        }
        int limit = 1 << zoom;
        if (x < 0 || x >= limit) {
            throw new IllegalArgumentException("Tile X must be in [0.." + (limit - 1) + "] at zoom " + zoom + ", got: " + x);
        }
        if (y < 0 || y >= limit) {
            throw new IllegalArgumentException("Tile Y must be in [0.." + (limit - 1) + "] at zoom " + zoom + ", got: " + y);
        }
    }

    /**
     * Converts this tile coordinate to its Bing Maps / Web Mercator QuadKey string.
     */
    public String toQuadKey() {
        return QuadKeyIndexer.tileToQuadKey(this);
    }
}
