package com.msp.matrix;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatrixTest {

    private static final double EPSILON = 1e-9;

    @Test
    @DisplayName("Matrix construction and factories")
    void testFactories() {
        Matrix zeros = Matrix.zeros(2, 3);
        assertEquals(2, zeros.rows());
        assertEquals(3, zeros.cols());
        assertEquals(0.0, zeros.get(0, 0));
        assertFalse(zeros.isSquare());

        Matrix identity3 = Matrix.identity(3);
        assertTrue(identity3.isSquare());
        assertEquals(1.0, identity3.get(0, 0));
        assertEquals(1.0, identity3.get(1, 1));
        assertEquals(1.0, identity3.get(2, 2));
        assertEquals(0.0, identity3.get(0, 1));
        assertEquals(3.0, identity3.trace());

        Matrix flat = Matrix.from(2, 2, 1, 2, 3, 4);
        assertEquals(1.0, flat.get(0, 0));
        assertEquals(2.0, flat.get(0, 1));
        assertEquals(3.0, flat.get(1, 0));
        assertEquals(4.0, flat.get(1, 1));
    }

    @Test
    @DisplayName("Matrix addition and subtraction")
    void testAddAndSubtract() {
        Matrix a = Matrix.from(2, 2, 1, 2, 3, 4);
        Matrix b = Matrix.from(2, 2, 5, 6, 7, 8);

        Matrix sum = a.add(b);
        Matrix expectedSum = Matrix.from(2, 2, 6, 8, 10, 12);
        assertTrue(sum.equals(expectedSum, EPSILON));

        Matrix diff = b.subtract(a);
        Matrix expectedDiff = Matrix.from(2, 2, 4, 4, 4, 4);
        assertTrue(diff.equals(expectedDiff, EPSILON));

        Matrix mismatched = Matrix.zeros(2, 3);
        assertThrows(IllegalArgumentException.class, () -> a.add(mismatched));
        assertThrows(IllegalArgumentException.class, () -> a.subtract(mismatched));
    }

    @Test
    @DisplayName("Scalar multiplication")
    void testScalarMultiply() {
        Matrix a = Matrix.from(2, 2, 2.0, -3.0, 4.0, 0.5);
        Matrix scaled = a.multiply(2.5);
        Matrix expected = Matrix.from(2, 2, 5.0, -7.5, 10.0, 1.25);
        assertTrue(scaled.equals(expected, EPSILON));
    }

    @Test
    @DisplayName("Matrix multiplication and identity preservation")
    void testMultiply() {
        // (2x3) * (3x2) -> (2x2)
        Matrix a = Matrix.from(2, 3,
                1, 2, 3,
                4, 5, 6
        );
        Matrix b = Matrix.from(3, 2,
                7, 8,
                9, 1,
                2, 3
        );

        Matrix product = a.multiply(b);
        assertEquals(2, product.rows());
        assertEquals(2, product.cols());

        // [1*7 + 2*9 + 3*2, 1*8 + 2*1 + 3*3] = [7+18+6, 8+2+9] = [31, 19]
        // [4*7 + 5*9 + 6*2, 4*8 + 5*1 + 6*3] = [28+45+12, 32+5+18] = [85, 55]
        Matrix expected = Matrix.from(2, 2, 31, 19, 85, 55);
        assertTrue(product.equals(expected, EPSILON));

        // Multiply by Identity preserves matrix
        Matrix identity3 = Matrix.identity(3);
        Matrix preserved = a.multiply(identity3);
        assertTrue(preserved.equals(a, EPSILON));

        Matrix incompatible = Matrix.zeros(2, 2);
        assertThrows(IllegalArgumentException.class, () -> a.multiply(incompatible));
    }

    @Test
    @DisplayName("Matrix transpose")
    void testTranspose() {
        Matrix a = Matrix.from(2, 3,
                1, 2, 3,
                4, 5, 6
        );
        Matrix at = a.transpose();
        assertEquals(3, at.rows());
        assertEquals(2, at.cols());

        Matrix expected = Matrix.from(3, 2,
                1, 4,
                2, 5,
                3, 6
        );
        assertTrue(at.equals(expected, EPSILON));
        // (A^T)^T == A
        assertTrue(at.transpose().equals(a, EPSILON));
    }

    @Test
    @DisplayName("Determinant of 1x1, 2x2, 3x3, and 4x4 matrices")
    void testDeterminant() {
        // 1x1
        Matrix m1 = Matrix.from(1, 1, 42.0);
        assertEquals(42.0, m1.determinant(), EPSILON);

        // 2x2: [[1, 2], [3, 4]] -> 1*4 - 2*3 = -2
        Matrix m2 = Matrix.from(2, 2, 1, 2, 3, 4);
        assertEquals(-2.0, m2.determinant(), EPSILON);

        // 3x3: [[6, 1, 1], [4, -2, 5], [2, 8, 7]] -> -306
        Matrix m3 = Matrix.from(3, 3,
                6, 1, 1,
                4, -2, 5,
                2, 8, 7
        );
        assertEquals(-306.0, m3.determinant(), EPSILON);

        // Non-square matrix determinant throws
        Matrix nonSquare = Matrix.zeros(2, 3);
        assertThrows(IllegalStateException.class, nonSquare::determinant);
    }

    @Test
    @DisplayName("Matrix inverse for 2x2 and verification A * A^-1 == I")
    void testInverse2x2() {
        Matrix a = Matrix.from(2, 2,
                4, 7,
                2, 6
        );
        // det = 4*6 - 7*2 = 24 - 14 = 10
        assertEquals(10.0, a.determinant(), EPSILON);

        Matrix inv = a.inverse();
        Matrix identity2 = Matrix.identity(2);

        Matrix product = a.multiply(inv);
        assertTrue(product.equals(identity2, EPSILON), "A * A^-1 must equal Identity");

        Matrix productReverse = inv.multiply(a);
        assertTrue(productReverse.equals(identity2, EPSILON), "A^-1 * A must equal Identity");
    }

    @Test
    @DisplayName("Matrix inverse for 3x3 and verification B * B^-1 == I")
    void testInverse3x3() {
        Matrix b = Matrix.from(3, 3,
                1, 2, 3,
                0, 1, 4,
                5, 6, 0
        );

        Matrix inv = b.inverse();
        Matrix identity3 = Matrix.identity(3);

        Matrix product = b.multiply(inv);
        assertTrue(product.equals(identity3, EPSILON), "B * B^-1 must equal Identity");
    }

    @Test
    @DisplayName("Matrix inverse for 4x4 via Gauss-Jordan and verification C * C^-1 == I")
    void testInverse4x4() {
        Matrix c = Matrix.from(4, 4,
                2, 1, 0, 0,
                1, 2, 1, 0,
                0, 1, 2, 1,
                0, 0, 1, 2
        );

        Matrix inv = c.inverse();
        Matrix identity4 = Matrix.identity(4);

        Matrix product = c.multiply(inv);
        assertTrue(product.equals(identity4, EPSILON), "C * C^-1 must equal Identity");
    }

    @Test
    @DisplayName("Singular matrix inverse throws ArithmeticException")
    void testSingularMatrixInverse() {
        // Linearly dependent rows: [[1, 2], [2, 4]] -> det = 0
        Matrix singular = Matrix.from(2, 2, 1, 2, 2, 4);
        assertEquals(0.0, singular.determinant(), EPSILON);
        assertThrows(ArithmeticException.class, singular::inverse);
    }

    @Test
    @DisplayName("Trace and validation checks")
    void testTraceAndBounds() {
        Matrix m = Matrix.from(3, 3,
                2, 0, 0,
                0, 5, 0,
                0, 0, -3
        );
        assertEquals(4.0, m.trace(), EPSILON);

        Matrix rect = Matrix.zeros(2, 3);
        assertThrows(IllegalStateException.class, rect::trace);
        assertThrows(IndexOutOfBoundsException.class, () -> m.get(5, 5));
    }
}
