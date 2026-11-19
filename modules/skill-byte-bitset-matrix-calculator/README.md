# 📦 `skill-byte-bitset-matrix-calculator`

> **64비트 Long 비트셋 기반 메모리 절약형 2D 불리언 비트 행렬(BitMatrix) 연산기**

- **아티팩트 좌표**: `com.msp:skill-byte-bitset-matrix-calculator:1.0.0`
- **개별 JAR 산출물**: `modules/skill-byte-bitset-matrix-calculator/build/libs/skill-byte-bitset-matrix-calculator-1.0.0.jar`
- **런타임 의존성**: **Zero** (Pure Java 21)

---

## 1. 주요 기능 (Features)

1. **초고밀도 메모리 압축 (64배 절감)**:
   - `boolean[][]` 대비 메모리 사용량을 $1/64$로 압축하여 수만 x 수만 크기의 거대 희소/밀집 그래프 행렬을 최소 메모리로 유지
2. **워드 단위 병렬 비트 연산 (In-place Row Bitwise)**:
   - 64비트 정수 연산으로 행 단위 AND, OR, XOR, NOT을 단일 사이클에 처리
3. **불리언 반환 행렬 곱셈 (Boolean Semi-Ring Multiplication)**:
   - $C = A \times B$ 연산을 비트 병렬 OR 연산으로 가속
4. **Warshall 알고리즘 기반 전이 폐포 (Transitive Closure / Reachability)**:
   - $O(V^3 / 64)$의 압도적인 속도로 방향 그래프의 도달 가능성(Reachability) 계산
5. **밀도 및 카디널리티 (Popcount) 계산**:
   - 행/열별 및 행렬 전체의 1-비트 수(`cardinality()`) 및 밀도(`density()`) 고속 산출

---

## 2. 사용 예제 (Usage Example)

```java
import com.msp.bitmatrix.BitMatrix;

public class BitMatrixExample {
    public static void main(String[] args) {
        // 1. 행렬 생성 및 비트 설정
        BitMatrix m = new BitMatrix(4, 4);
        m.set(0, 1, true); // 0 -> 1
        m.set(1, 2, true); // 1 -> 2
        m.set(2, 3, true); // 2 -> 3

        System.out.println("초기 밀도: " + m.density());
        System.out.println("1-비트 총합: " + m.cardinality());

        // 2. Warshall 전이 폐포 계산 (그래프 전체 도달 가능성 탐색)
        BitMatrix reachable = m.transitiveClosure();
        System.out.println("0번 노드에서 3번 노드 도달 가능 여부: " + reachable.get(0, 3)); // true

        // 3. 행렬 곱셈 (A x B)
        BitMatrix a = BitMatrix.fromBinaryStrings("10", "01");
        BitMatrix b = BitMatrix.fromBinaryStrings("01", "10");
        BitMatrix c = a.multiply(b);
        System.out.println("곱셈 결과:\n" + c.toBinaryString());
    }
}
```
