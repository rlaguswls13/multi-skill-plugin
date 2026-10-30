# 🏷️ Strict SemVer 2.0.0 Comparator (`skill-version-comparator-strict`)

> Zero-dependency, thread-safe, pure Java 21 implementation strictly conforming to the **Semantic Versioning 2.0.0 Specification**.

---

## 🌟 Key Features

1. **Full SemVer 2.0.0 Specification Compliance**:
   - Parses `MAJOR.MINOR.PATCH[-PRERELEASE][+BUILD]`.
   - Rejects illegal leading zeroes in numbers (`01.2.3` or `1.0.0-01`).
2. **Standard Precedence Implementation**:
   - Normal release has higher precedence than pre-release (`1.0.0 > 1.0.0-rc.1`).
   - Numerical vs alphabetical identifier ordering per specification.
   - Build metadata ignored in precedence checks (`1.0.0+001 == 1.0.0+2026`).
3. **Pure Java 21 & Zero Dependencies**:
   - Zero runtime dependencies outside the JDK standard library.

---

## 🚀 Quick Start

```java
import com.msp.semvercomp.StrictSemVer;

// 1. Parse versions
StrictSemVer v1 = StrictSemVer.parse("1.0.0-alpha.1");
StrictSemVer v2 = StrictSemVer.parse("1.0.0-alpha.beta");
StrictSemVer v3 = StrictSemVer.parse("1.0.0");

// 2. Compare precedence
boolean isLess = v1.isLessThan(v2); // true
boolean isGreater = v3.isGreaterThan(v1); // true

// 3. Build metadata is ignored in precedence comparison
StrictSemVer b1 = StrictSemVer.parse("1.0.0+build1");
StrictSemVer b2 = StrictSemVer.parse("1.0.0+build2");
assert b1.compareTo(b2) == 0;
assert b1.isEquivalentTo(b2); // true
```
