package com.msp.quadkey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Web Mercator (EPSG:3857) Tile &amp; QuadKey Spatial Indexer for WGS84 Lat/Lng coordinates.
 * <p>
 * Supports bidirectional conversion between {@code (latitude, longitude, zoom)}, {@link TileCoord},
 * and base-4 {@code QuadKey} strings (`0, 1, 2, 3`), bounding box calculations, 8-neighbor traversal,
 * hierarchical parent/child lookup, and minimal prefix range generation for spatial bounding-box queries.
 */
public final class QuadKeyIndexer {

    public static final double MIN_LATITUDE = -85.05112878;
    public static final double MAX_LATITUDE = 85.05112878;
    public static final double MIN_LONGITUDE = -180.0;
    public static final double MAX_LONGITUDE = 180.0;

    private QuadKeyIndexer() {
    }

    public static double clip(double n, double minValue, double maxValue) {
        return Math.min(Math.max(n, minValue), maxValue);
    }

    /**
     * Converts a WGS84 {@code (latitude, longitude)} at {@code zoom} (1..23) into a {@link TileCoord}.
     */
    public static TileCoord latLonToTile(double latitude, double longitude, int zoom) {
        validateZoom(zoom);
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
            throw new IllegalArgumentException("Latitude and longitude must not be NaN");
        }
        double lat = clip(latitude, MIN_LATITUDE, MAX_LATITUDE);
        double lon = clip(longitude, MIN_LONGITUDE, MAX_LONGITUDE);

        double xRatio = (lon + 180.0) / 360.0;
        double sinLat = Math.sin(Math.toRadians(lat));
        double yRatio = 0.5 - Math.log((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * Math.PI);

        int mapTileCount = 1 << zoom;
        int tileX = (int) clip(Math.floor(xRatio * mapTileCount), 0, mapTileCount - 1);
        int tileY = (int) clip(Math.floor(yRatio * mapTileCount), 0, mapTileCount - 1);
        return new TileCoord(tileX, tileY, zoom);
    }

    /**
     * Converts a WGS84 {@code (latitude, longitude)} at {@code zoom} (1..23) directly into a QuadKey string.
     */
    public static String latLonToQuadKey(double latitude, double longitude, int zoom) {
        return tileToQuadKey(latLonToTile(latitude, longitude, zoom));
    }

    /**
     * Interleaves the bits of {@code tile.x()} and {@code tile.y()} into a base-4 QuadKey string of length {@code zoom}.
     */
    public static String tileToQuadKey(TileCoord tile) {
        Objects.requireNonNull(tile, "tile must not be null");
        int zoom = tile.zoom();
        char[] chars = new char[zoom];
        for (int i = zoom; i > 0; i--) {
            int digit = 0;
            int mask = 1 << (i - 1);
            if ((tile.x() & mask) != 0) {
                digit += 1;
            }
            if ((tile.y() & mask) != 0) {
                digit += 2;
            }
            chars[zoom - i] = (char) ('0' + digit);
        }
        return new String(chars);
    }

    /**
     * Decodes a base-4 QuadKey string (length 1..23, digits {@code 0..3}) into its {@link TileCoord}.
     */
    public static TileCoord quadKeyToTile(String quadKey) {
        validateQuadKey(quadKey);
        int zoom = quadKey.length();
        int tileX = 0;
        int tileY = 0;
        for (int i = zoom; i > 0; i--) {
            int mask = 1 << (i - 1);
            char c = quadKey.charAt(zoom - i);
            switch (c) {
                case '0' -> {
                }
                case '1' -> tileX |= mask;
                case '2' -> tileY |= mask;
                case '3' -> {
                    tileX |= mask;
                    tileY |= mask;
                }
                default -> throw new IllegalArgumentException("Invalid QuadKey digit '" + c + "' in: " + quadKey);
            }
        }
        return new TileCoord(tileX, tileY, zoom);
    }

    /**
     * Computes the geographic bounding box ({@code minLat, minLon, maxLat, maxLon}) for a {@link TileCoord}.
     */
    public static GeoBoundingBox tileToBoundingBox(TileCoord tile) {
        Objects.requireNonNull(tile, "tile must not be null");
        double n = 1 << tile.zoom();
        double minLon = (tile.x() / n) * 360.0 - 180.0;
        double maxLon = ((tile.x() + 1.0) / n) * 360.0 - 180.0;

        // Note: tile Y=0 is at the North (+85.0511), so tile.y() corresponds to maxLat and tile.y()+1 to minLat
        double maxLat = tileYToLatitude(tile.y(), n);
        double minLat = tileYToLatitude(tile.y() + 1, n);
        return new GeoBoundingBox(minLat, minLon, maxLat, maxLon);
    }

    /**
     * Computes the geographic bounding box for a QuadKey string.
     */
    public static GeoBoundingBox quadKeyToBoundingBox(String quadKey) {
        return tileToBoundingBox(quadKeyToTile(quadKey));
    }

    private static double tileYToLatitude(int y, double n) {
        double latRad = Math.atan(Math.sinh(Math.PI * (1.0 - 2.0 * y / n)));
        return Math.toDegrees(latRad);
    }

    /**
     * Returns the 8 surrounding neighbor tiles at the same zoom level.
     * Longitude (X) wraps around the antimeridian; latitude (Y) is clamped within valid polar tile limits.
     */
    public static List<TileCoord> neighbors(TileCoord tile) {
        Objects.requireNonNull(tile, "tile must not be null");
        int limit = 1 << tile.zoom();
        Set<TileCoord> result = new LinkedHashSet<>(8);
        for (int dy = -1; dy <= 1; dy++) {
            int ny = tile.y() + dy;
            if (ny < 0 || ny >= limit) {
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                int nx = (tile.x() + dx + limit) & (limit - 1);
                result.add(new TileCoord(nx, ny, tile.zoom()));
            }
        }
        return List.copyOf(result);
    }

    /**
     * Returns the neighboring QuadKeys at the same zoom level (up to 8 neighbors).
     */
    public static List<String> neighborQuadKeys(String quadKey) {
        TileCoord center = quadKeyToTile(quadKey);
        List<TileCoord> neighborTiles = neighbors(center);
        List<String> out = new ArrayList<>(neighborTiles.size());
        for (TileCoord t : neighborTiles) {
            out.add(tileToQuadKey(t));
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Returns the parent tile at {@code zoom - 1}.
     */
    public static TileCoord parent(TileCoord tile) {
        Objects.requireNonNull(tile, "tile must not be null");
        if (tile.zoom() <= TileCoord.MIN_ZOOM) {
            throw new IllegalArgumentException("Tile at zoom 1 has no parent tile (zoom 0)");
        }
        return new TileCoord(tile.x() >>> 1, tile.y() >>> 1, tile.zoom() - 1);
    }

    /**
     * Returns the parent QuadKey at {@code zoom - 1} (prefix of length {@code length - 1}).
     */
    public static String parentQuadKey(String quadKey) {
        validateQuadKey(quadKey);
        if (quadKey.length() <= TileCoord.MIN_ZOOM) {
            throw new IllegalArgumentException("QuadKey of length 1 has no parent QuadKey");
        }
        return quadKey.substring(0, quadKey.length() - 1);
    }

    /**
     * Returns the 4 children tiles at {@code zoom + 1} in Morton Z-order (0: NW, 1: NE, 2: SW, 3: SE).
     */
    public static List<TileCoord> children(TileCoord tile) {
        Objects.requireNonNull(tile, "tile must not be null");
        if (tile.zoom() >= TileCoord.MAX_ZOOM) {
            throw new IllegalArgumentException("Tile at maximum zoom 23 cannot be subdivided further");
        }
        int nextZoom = tile.zoom() + 1;
        int bx = tile.x() << 1;
        int by = tile.y() << 1;
        return List.of(
                new TileCoord(bx, by, nextZoom),
                new TileCoord(bx + 1, by, nextZoom),
                new TileCoord(bx, by + 1, nextZoom),
                new TileCoord(bx + 1, by + 1, nextZoom)
        );
    }

    /**
     * Returns the 4 children QuadKeys at {@code zoom + 1} ({@code quadKey + "0"}, {@code "1"}, {@code "2"}, {@code "3"}).
     */
    public static List<String> childrenQuadKeys(String quadKey) {
        validateQuadKey(quadKey);
        if (quadKey.length() >= TileCoord.MAX_ZOOM) {
            throw new IllegalArgumentException("QuadKey at maximum zoom 23 cannot have children");
        }
        return List.of(quadKey + "0", quadKey + "1", quadKey + "2", quadKey + "3");
    }

    /**
     * Computes the covering set of QuadKeys for a geographic bounding box at the specified {@code zoom},
     * coalescing complete 4-sibling quads into shorter parent prefix ranges for B-Tree range queries.
     *
     * @param bbox     geographic bounding box to cover
     * @param zoom     target resolution zoom level (1..23)
     * @param maxTiles safety limit on raw tile count at target zoom to prevent unbounded enumeration
     */
    public static List<QuadKeyPrefixRange> coverBoundingBoxWithPrefixRanges(
            GeoBoundingBox bbox,
            int zoom,
            int maxTiles) {
        Objects.requireNonNull(bbox, "bbox must not be null");
        validateZoom(zoom);
        if (maxTiles <= 0) {
            throw new IllegalArgumentException("maxTiles must be > 0");
        }

        // Top-left (North-West) has minLon, maxLat; Bottom-right (South-East) has maxLon, minLat
        TileCoord nw = latLonToTile(bbox.maxLat(), bbox.minLon(), zoom);
        TileCoord se = latLonToTile(bbox.minLat(), bbox.maxLon(), zoom);

        long width = (long) se.x() - nw.x() + 1;
        long height = (long) se.y() - nw.y() + 1;
        if (width * height > maxTiles) {
            throw new IllegalArgumentException(
                    "Bounding box covers " + (width * height) + " tiles at zoom " + zoom + ", exceeding maxTiles=" + maxTiles);
        }

        TreeSet<String> keys = new TreeSet<>();
        for (int y = nw.y(); y <= se.y(); y++) {
            for (int x = nw.x(); x <= se.x(); x++) {
                keys.add(tileToQuadKey(new TileCoord(x, y, zoom)));
            }
        }

        // Bottom-up quadtree coalescing: whenever all 4 siblings (prefix + 0,1,2,3) exist, merge into prefix
        boolean merged = true;
        while (merged) {
            merged = false;
            List<String> snapshot = new ArrayList<>(keys);
            for (String k : snapshot) {
                if (k.length() > 1 && k.endsWith("0") && keys.contains(k)) {
                    String prefix = k.substring(0, k.length() - 1);
                    String s0 = prefix + "0";
                    String s1 = prefix + "1";
                    String s2 = prefix + "2";
                    String s3 = prefix + "3";
                    if (keys.contains(s0) && keys.contains(s1) && keys.contains(s2) && keys.contains(s3)) {
                        keys.remove(s0);
                        keys.remove(s1);
                        keys.remove(s2);
                        keys.remove(s3);
                        keys.add(prefix);
                        merged = true;
                    }
                }
            }
        }

        List<QuadKeyPrefixRange> ranges = new ArrayList<>(keys.size());
        for (String prefix : keys) {
            int pad = zoom - prefix.length();
            String minKey = prefix + "0".repeat(pad);
            String maxKey = prefix + "3".repeat(pad);
            ranges.add(new QuadKeyPrefixRange(prefix, minKey, maxKey, zoom));
        }
        return Collections.unmodifiableList(ranges);
    }

    private static void validateZoom(int zoom) {
        if (zoom < TileCoord.MIN_ZOOM || zoom > TileCoord.MAX_ZOOM) {
            throw new IllegalArgumentException("Zoom level must be between 1 and 23, got: " + zoom);
        }
    }

    private static void validateQuadKey(String quadKey) {
        if (quadKey == null || quadKey.isEmpty() || quadKey.length() > TileCoord.MAX_ZOOM) {
            throw new IllegalArgumentException("QuadKey length must be in [1..23], got: " + quadKey);
        }
        for (int i = 0; i < quadKey.length(); i++) {
            char c = quadKey.charAt(i);
            if (c < '0' || c > '3') {
                throw new IllegalArgumentException("Invalid QuadKey character '" + c + "' at index " + i);
            }
        }
    }
}
