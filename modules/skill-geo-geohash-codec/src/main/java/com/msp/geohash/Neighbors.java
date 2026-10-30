package com.msp.geohash;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Encapsulates the 8 cardinal and intercardinal neighbors surrounding a GeoHash cell.
 */
public record Neighbors(
        String north,
        String northEast,
        String east,
        String southEast,
        String south,
        String southWest,
        String west,
        String northWest
) implements Serializable {

    public Neighbors {
        Objects.requireNonNull(north, "north cannot be null");
        Objects.requireNonNull(northEast, "northEast cannot be null");
        Objects.requireNonNull(east, "east cannot be null");
        Objects.requireNonNull(southEast, "southEast cannot be null");
        Objects.requireNonNull(south, "south cannot be null");
        Objects.requireNonNull(southWest, "southWest cannot be null");
        Objects.requireNonNull(west, "west cannot be null");
        Objects.requireNonNull(northWest, "northWest cannot be null");
    }

    /**
     * Returns an unmodifiable list of all 8 surrounding neighbor hashes in order:
     * [N, NE, E, SE, S, SW, W, NW].
     */
    public List<String> toList() {
        return List.of(north, northEast, east, southEast, south, southWest, west, northWest);
    }

    /**
     * Returns a map with directional keys: "N", "NE", "E", "SE", "S", "SW", "W", "NW".
     */
    public Map<String, String> toMap() {
        return Map.of(
                "N", north,
                "NE", northEast,
                "E", east,
                "SE", southEast,
                "S", south,
                "SW", southWest,
                "W", west,
                "NW", northWest
        );
    }
}
