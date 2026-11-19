# skill-ip-geo-encoded-polyline-codec

Zero-dependency Java 21 **Google Encoded Polyline Algorithm (Precision 5 & 6)** codec, **Douglas-Peucker** path simplifier, and **Haversine** path length calculator.

## Architecture Highlights
- **Bidirectional Codec**: Exact Google Encoded Polyline Algorithm for `PRECISION_5` (`1e5`) and `PRECISION_6` (`1e6`, OSRM/Valhalla)
- **Stack-Safe Douglas-Peucker**: Iterative simplification supporting both meter tolerance (local equirectangular tangent-plane projection) and decimal-degree tolerance
- **WGS84 Geometry**: Haversine great-circle distance, multi-segment path length, and clamped point-to-segment perpendicular distance

## Usage Example
```java
import com.msp.polyline.*;
import java.util.List;

List<GeoPoint> route = List.of(
        new GeoPoint(38.5, -120.2),
        new GeoPoint(40.7, -120.95),
        new GeoPoint(43.252, -126.453)
);

String encoded = EncodedPolylineCodec.encode(route); // "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
List<GeoPoint> decoded = EncodedPolylineCodec.decode(encoded);

List<GeoPoint> simplified = PolylineSimplifier.simplify(decoded, 25.0); // 25m tolerance
double lengthMeters = GeoDistance.pathLengthMeters(decoded);
```
