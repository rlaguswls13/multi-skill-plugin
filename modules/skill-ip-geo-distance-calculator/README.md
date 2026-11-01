# 🌍 `skill-ip-geo-distance-calculator`

> **순수 Java 21 표준 라이브러리 기반 Haversine 및 Vincenty WGS-84 고정밀 측지선 거리/방위각 계산기**

- **아티팩트 좌표**: `com.msp:skill-ip-geo-distance-calculator:1.0.0`
- **개별 JAR 산출물**: `modules/skill-ip-geo-distance-calculator/build/libs/skill-ip-geo-distance-calculator-1.0.0.jar`
- **외부 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 특징 (Features)

1. **Haversine 대원 거리 알고리즘**:
   - IUGG 평균 지구 반경 $6371008.8\text{m}$ 기반의 구면 삼각 측량 거리 계산
   - 계산 속도가 매우 빨라 실시간 대량 반경 필터링(`isWithinRadius`)에 최적
2. **Vincenty 고정밀 타원체 알고리즘 (WGS-84)**:
   - 지구의 편평도($f = 1 / 298.257223563$)를 반영한 타원체 측지선 반복 수렴 공식
   - 밀리미터 단위 오차 범위의 최고 정밀도 제공
   - 대척점(Antipodal points) 등 수렴 한계 상황 발생 시 안전한 Haversine 자동 폴백 보장
3. **초기 방위각 (Initial Bearing / Forward Azimuth)**:
   - 출발점에서 도착점을 향하는 초기 진행 방향 나침반 각도($0^\circ \sim 360^\circ$) 계산
4. **엄격한 좌표 유효성 검증 (`GeoPoint`)**:
   - 위도 $[-90.0, 90.0]$, 경도 $[-180.0, 180.0]$, NaN 및 Infinity 차단 불변 레코드

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.geodist.GeoDistanceCalculator;
import com.msp.geodist.GeoPoint;

public class GeoExample {
    public static void main(String[] args) {
        GeoPoint seoul = GeoPoint.of(37.5665, 126.9780);
        GeoPoint newYork = GeoPoint.of(40.7128, -74.0060);

        // 1. Haversine 거리 (구면 모델, 빠른 연산)
        double haversineDist = GeoDistanceCalculator.haversineMeters(seoul, newYork);
        System.out.printf("Haversine Distance: %.2f km%n", haversineDist / 1000.0);

        // 2. Vincenty 거리 (WGS-84 타원체 정밀 연산)
        double vincentyDist = GeoDistanceCalculator.vincentyMeters(seoul, newYork);
        System.out.printf("Vincenty Distance: %.2f km%n", vincentyDist / 1000.0);

        // 3. 초기 방위각 (진행 방향)
        double bearing = GeoDistanceCalculator.initialBearingDegrees(seoul, newYork);
        System.out.printf("Initial Bearing: %.2f°%n", bearing);

        // 4. 특정 반경 이내 포함 여부 (예: 서울 기준 1,000km 이내)
        boolean within1000km = GeoDistanceCalculator.isWithinRadius(seoul, newYork, 1_000_000.0);
        System.out.println("Within 1,000km: " + within1000km); // false
    }
}
```
