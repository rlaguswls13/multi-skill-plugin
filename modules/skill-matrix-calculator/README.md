# 🧮 skill-matrix-calculator

> **2D Matrix Linear Algebra Calculator for Java 21**  
> Pure Java 21 standard library, immutable matrix structures supporting arithmetic, transpose, trace, determinant, and analytical / Gauss-Jordan inversion.

---

## 🚀 Key Features

- **Zero Runtime Dependencies**: Standard Java 21 only.
- **Immutable & Thread-Safe**: All operations produce new immutable `Matrix` instances.
- **Basic Arithmetic**: Addition, subtraction, scalar multiplication, and matrix dot product multiplication.
- **Determinants**: Fast analytical determinant for $1\times 1$, $2\times 2$, $3\times 3$, and Gaussian elimination for general $N\times N$.
- **Matrix Inversion**: High-accuracy inversion ($A \times A^{-1} \approx I$) with singular matrix singularity detection.
- **Validation**: Strict dimension compatibility checks with informative exception messages.

---

## 📦 Gradle Dependency

```groovy
dependencies {
    implementation 'com.msp:skill-matrix-calculator:1.0.0'
}
```

---

## 💡 Usage Examples

### 1. Matrix Creation & Multiplication

```java
import com.msp.matrix.Matrix;

// Construct 2x3 and 3x2 matrices
Matrix a = Matrix.from(2, 3,
    1.0, 2.0, 3.0,
    4.0, 5.0, 6.0
);

Matrix b = Matrix.from(3, 2,
    7.0, 8.0,
    9.0, 1.0,
    2.0, 3.0
);

// Dot-product multiplication -> (2x2)
Matrix result = a.multiply(b);
System.out.println(result);
```

### 2. Determinant and Matrix Inversion

```java
import com.msp.matrix.Matrix;

Matrix m = Matrix.from(3, 3,
    1.0, 2.0, 3.0,
    0.0, 1.0, 4.0,
    5.0, 6.0, 0.0
);

double det = m.determinant();
System.out.println("Determinant: " + det);

// Compute inverse: A^-1
Matrix inv = m.inverse();

// Verify: A * A^-1 == Identity
Matrix identity = m.multiply(inv);
System.out.println("A * A^-1 is Identity: " + identity.equals(Matrix.identity(3), 1e-9));
```
