# 🌍 skill-geo-distance

> **Haversine great-circle distance (km, meters) and geographical bounding box calculator**  
> Pure Java 21 zero-dependency library providing spherical geodesic distance calculations, forward azimuth initial bearing, and bounding boxes with antimeridian (180° meridian) and polar cap support.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Pure Java 21 standard library.
- **Geodesic Distance**:
  - `GeoDistance.distanceInMeters(c1, c2)`
  - `GeoDistance.distanceInKm(c1, c2)`
  - `GeoDistance.isWithinRadius(c1, c2, radiusMeters)`
- **Navigation & Compass Bearing**:
  - `GeoDistance.initialBearing(c1, c2)`: computes forward azimuth in degrees $[0.0, 360.0)$.
- **Spatial Bounding Box**:
  - `GeoBoundingBox.of(center, radiusMeters)`: computes rectangular bounding range for fast spatial query pre-filtering in SQL/indexes.
  - Full support for the International Date Line (antimeridian wrapping) and polar caps.
- **Type-Safe Record**: `GeoCoordinate(latitude, longitude)` with strict range validation ($-90^\circ$ to $+90^\circ$, $-180^\circ$ to $+180^\circ$).

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-geo-distance:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Calculating Distance and Radius Checking

```java
import com.msp.geo.GeoCoordinate;
import com.msp.geo.GeoDistance;

GeoCoordinate seoul = new GeoCoordinate(37.5665, 126.9780);
GeoCoordinate busan = new GeoCoordinate(35.1796, 129.0756);

double distanceKm = GeoDistance.distanceInKm(seoul, busan);
System.out.printf("Seoul to Busan: %.2f km%n", distanceKm); // ~325.30 km

// Proximity check (e.g. within 50 km delivery radius)
boolean inRange = GeoDistance.isWithinRadius(seoul, busan, 50_000); // false
```

### 2. Forward Compass Bearing (Direction of Travel)

```java
GeoCoordinate london = new GeoCoordinate(51.5074, -0.1278);
GeoCoordinate paris = new GeoCoordinate(48.8566, 2.3522);

double bearing = GeoDistance.initialBearing(london, paris);
System.out.printf("Initial bearing from London to Paris: %.1f°%n", bearing); // ~148.7° (SSE)
```

### 3. Spatial Bounding Box for SQL Range Queries

```java
import com.msp.geo.GeoBoundingBox;

GeoCoordinate userLocation = new GeoCoordinate(37.5665, 126.9780);
double searchRadiusMeters = 5_000; // 5 km search circle

GeoBoundingBox box = GeoBoundingBox.of(userLocation, searchRadiusMeters);

// Use bounding box in SQL query for index optimization:
// SELECT * FROM stores WHERE lat BETWEEN ? AND ? AND lon BETWEEN ? AND ?
System.out.println("Min Lat: " + box.minLat());
System.out.println("Max Lat: " + box.maxLat());
System.out.println("Min Lon: " + box.minLon());
System.out.println("Max Lon: " + box.maxLon());

// In-memory containment check
GeoCoordinate store = new GeoCoordinate(37.5670, 126.9800);
if (box.contains(store)) {
    System.out.println("Store is within bounding box candidates!");
}
```
