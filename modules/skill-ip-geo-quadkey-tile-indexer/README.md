# skill-ip-geo-quadkey-tile-indexer

Web Mercator Tile (`X, Y, Zoom 1..23`) & Morton Z-Order QuadKey Spatial Indexer for WGS84 Lat/Lng coordinates in pure Java 21.

## Architecture Highlights
- **Bidirectional Coordinate & QuadKey Indexing**: Converts `(latitude, longitude, zoom)` $\leftrightarrow$ `TileCoord(x, y, zoom)` $\leftrightarrow$ base-4 `QuadKey` string (`0, 1, 2, 3`).
- **Spatial Hierarchy & Neighbors**: Fast bit-interleaved computation of 8 adjacent neighbors (with antimeridian wrapping), parent tile (`zoom - 1`), and 4 children tiles (`zoom + 1`).
- **Prefix Coalescing for B-Tree Range Scans**: `coverBoundingBoxWithPrefixRanges` covers any geographic bounding box and merges complete 4-sibling quads into minimal prefix ranges (`[prefix0..0, prefix3..3]`).

## Usage Example

```java
import com.msp.quadkey.*;
import java.util.List;

// Index Seoul City Hall at zoom 15
String quadKey = QuadKeyIndexer.latLonToQuadKey(37.5665, 126.9780, 15);
GeoBoundingBox bbox = QuadKeyIndexer.quadKeyToBoundingBox(quadKey);
List<String> neighbors = QuadKeyIndexer.neighborQuadKeys(quadKey);
```
