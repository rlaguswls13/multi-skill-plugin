# 🌐 GeoHash Codec (`skill-geo-geohash-codec`)

> Zero-dependency, high-performance, pure Java 21 implementation of the standard **Base32 GeoHash** encoding, decoding, and 8-direction spatial neighbor calculation.

---

## 🌟 Key Features

1. **Standard Base32 Alphabet**:
   - Uses `0123456789bcdefghjkmnpqrstuvwxyz` (excluding `a`, `i`, `l`, `o` to avoid visual ambiguity).
2. **Bidirectional Encoding & Decoding**:
   - `encode(lat, lng, precision)`: transforms WGS84 coordinates into hierarchical string tokens.
   - `decode(geohash)`: restores `BoundingBox` with min/max intervals and centroid.
3. **Spatial 8-Neighbor Calculation**:
   - Instantly computes N, NE, E, SE, S, SW, W, NW neighboring hash blocks for proximity searches.
4. **Zero Dependencies**:
   - Pure Java 21 standard library, thread-safe, and allocation-efficient.

---

## 🚀 Quick Start

```java
import com.msp.geohash.GeoHashCodec;
import com.msp.geohash.BoundingBox;
import com.msp.geohash.Neighbors;

// 1. Encode coordinates
String seoulHash = GeoHashCodec.encode(37.5665, 126.9780, 6); // "wydm9q"

// 2. Decode back to bounding box and centroid
BoundingBox box = GeoHashCodec.decode(seoulHash);
double centerLat = box.centerLat(); // ~37.5665
double centerLng = box.centerLng(); // ~126.9780
boolean inside = box.contains(37.5665, 126.9780); // true

// 3. Find 8 surrounding spatial neighbors
Neighbors neighbors = GeoHashCodec.getNeighbors(seoulHash);
String northCell = neighbors.north();
List<String> all8Neighbors = neighbors.toList();
```
