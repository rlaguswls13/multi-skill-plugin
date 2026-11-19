# 📦 `skill-ip-geo-polygon-geofence`

> **순수 Java 21 표준 라이브러리 기반 레이 캐스팅(Ray Casting) 지오펜스 폴리곤 포함 여부 판별기 및 공간 거리 연산기 (O(1) Bounding Box 고속 기각, 오목/볼록 다각형 지원)**

- **아티팩트 좌표**: `com.msp:skill-ip-geo-polygon-geofence:1.0.0`
- **개별 JAR 산출물**: `modules/skill-ip-geo-polygon-geofence/build/libs/skill-ip-geo-polygon-geofence-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **Ray Casting (Even-Odd Rule) 다각형 포함 판정**:
   - 볼록(Convex) 및 오목(Concave, U자형/L자형) 폴리곤의 $O(V)$ 정확한 좌표 포함 여부 판정.
   - 선분 경계(Boundary Edge) 및 정점 통과(Collinear) 엣지 케이스 완벽 처리.
2. **$O(1)$ Bounding Box 사전 기각 최적화**:
   - 최소 경계 사각형(Envelope)을 이용해 외부 좌표에 대해 레이 캐스팅 연산 없이 즉각 기각.
3. **공간 기하학 연산**:
   - Haversine 구면 대원 거리(Great-circle distance, 미터 단위 정밀도).
   - 다각형 무게중심(Centroid) 및 최소 외접원(Minimum Bounding Circle) 계산.
   - 외부 좌표와 다각형 외곽선 간 최단 거리(`distanceTo`) 연산.
4. **다중 지오펜스 레지스트리 (`GeofenceRegistry`)**:
   - 동시성 제어(`ConcurrentHashMap`) 기반 다중 구역 관리.
   - 특정 좌표가 포함된 모든 구역 조회(`findContaining`).
   - 특정 좌표에서 가장 가까운 구역 및 거리 산출(`findNearest`).

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.geofence.*;
import java.util.List;

public class Example {
    public static void main(String[] args) {
        // 1. 서울 강남 지오펜스 폴리곤 정의
        GeofencePolygon gangnamZone = GeofencePolygon.of(
                "zone-gangnam",
                "Gangnam Delivery Zone",
                List.of(
                        GeoPoint.of(37.4900, 127.0200),
                        GeoPoint.of(37.4900, 127.0400),
                        GeoPoint.of(37.5100, 127.0400),
                        GeoPoint.of(37.5100, 127.0200)
                )
        );

        // 2. 레지스트리 등록 및 좌표 검사
        GeofenceRegistry registry = new GeofenceRegistry();
        registry.register(gangnamZone);

        GeoPoint userLocation = GeoPoint.of(37.5000, 127.0300);

        // 구역 포함 여부 확인
        boolean isInside = gangnamZone.contains(userLocation);
        System.out.println("User is inside Gangnam Zone: " + isInside);

        // 가장 가까운 구역 및 거리 검색
        GeoPoint outsideUser = GeoPoint.of(37.5200, 127.0300);
        registry.findNearest(outsideUser).ifPresent(result -> {
            System.out.printf("Nearest Zone: %s, Distance: %.1f meters%n",
                    result.polygon().name(), result.distanceMeters());
        });
    }
}
```
