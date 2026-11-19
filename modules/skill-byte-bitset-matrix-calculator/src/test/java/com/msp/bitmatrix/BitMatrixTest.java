package com.msp.bitmatrix;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BitMatrix Tests")
class BitMatrixTest {

    @Nested
    @DisplayName("Creation and Basic Operations")
    class BasicOperationsTests {

        @Test
        @DisplayName("Initializes with all zeros")
        void initialZeros() {
            BitMatrix m = new BitMatrix(4, 5);
            assertEquals(4, m.rows());
            assertEquals(5, m.cols());
            assertEquals(0, m.cardinality());
            assertTrue(m.isEmpty());

            for (int r = 0; r < 4; r++) {
                for (int c = 0; c < 5; c++) {
                    assertFalse(m.get(r, c));
                }
            }
        }

        @Test
        @DisplayName("Sets, gets, and flips individual bits")
        void setGetFlip() {
            BitMatrix m = new BitMatrix(3, 3);
            m.set(1, 2, true);
            assertTrue(m.get(1, 2));
            assertFalse(m.get(0, 0));

            m.flip(1, 2);
            assertFalse(m.get(1, 2));

            m.flip(0, 0);
            assertTrue(m.get(0, 0));

            m.set(0, 0, false);
            assertFalse(m.get(0, 0));
        }

        @Test
        @DisplayName("Handles widths larger than 64 bits (multiple long words)")
        void largeWidth() {
            BitMatrix m = new BitMatrix(2, 130);
            m.set(0, 0, true);
            m.set(0, 63, true);
            m.set(0, 64, true);
            m.set(0, 127, true);
            m.set(0, 129, true);

            assertEquals(5, m.cardinality());
            assertEquals(5, m.rowCardinality(0));
            assertEquals(0, m.rowCardinality(1));

            assertTrue(m.get(0, 0));
            assertTrue(m.get(0, 63));
            assertTrue(m.get(0, 64));
            assertTrue(m.get(0, 127));
            assertTrue(m.get(0, 129));
            assertFalse(m.get(0, 65));
        }

        @Test
        @DisplayName("Fill and clear whole matrix and specific rows")
        void fillAndClear() {
            BitMatrix m = new BitMatrix(3, 4);
            m.fill();
            assertEquals(12, m.cardinality());
            assertEquals(1.0, m.density());

            m.clearRow(1);
            assertEquals(8, m.cardinality());
            assertEquals(0, m.rowCardinality(1));
            assertEquals(4, m.rowCardinality(0));

            m.fillRow(1);
            assertEquals(12, m.cardinality());

            m.clear();
            assertEquals(0, m.cardinality());
            assertTrue(m.isEmpty());
        }
    }

    @Nested
    @DisplayName("Row-Level Bitwise Operations")
    class RowBitwiseTests {

        @Test
        @DisplayName("In-place row AND, OR, XOR, NOT")
        void rowBitwise() {
            // Row 0: 1 1 0 0
            // Row 1: 1 0 1 0
            BitMatrix m = BitMatrix.fromBinaryStrings("1100", "1010", "0000");

            // Row 2 = Row 0 OR Row 1 -> 1 1 1 0
            m.rowOr(2, 0);
            m.rowOr(2, 1);
            assertEquals(3, m.rowCardinality(2));
            assertTrue(m.get(2, 0));
            assertTrue(m.get(2, 1));
            assertTrue(m.get(2, 2));
            assertFalse(m.get(2, 3));

            // Row 0 AND Row 1 -> 1 0 0 0
            m.rowAnd(0, 1);
            assertEquals(1, m.rowCardinality(0));
            assertTrue(m.get(0, 0));

            // Row 1 XOR Row 1 -> 0 0 0 0
            m.rowXor(1, 1);
            assertEquals(0, m.rowCardinality(1));

            // Row 1 NOT -> 1 1 1 1
            m.rowNot(1);
            assertEquals(4, m.rowCardinality(1));
        }
    }

    @Nested
    @DisplayName("Matrix-Level Bitwise Operations")
    class MatrixBitwiseTests {

        @Test
        @DisplayName("Matrix AND, OR, XOR, NOT")
        void matrixBitwise() {
            BitMatrix a = BitMatrix.fromBinaryStrings("110", "011");
            BitMatrix b = BitMatrix.fromBinaryStrings("101", "001");

            BitMatrix andM = a.and(b);
            assertEquals("100\n001", andM.toBinaryString());

            BitMatrix orM = a.or(b);
            assertEquals("111\n011", orM.toBinaryString());

            BitMatrix xorM = a.xor(b);
            assertEquals("011\n010", xorM.toBinaryString());

            BitMatrix notA = a.not();
            assertEquals("001\n100", notA.toBinaryString());
        }
    }

    @Nested
    @DisplayName("Matrix Multiplication and Reachability")
    class MultiplicationAndReachabilityTests {

        @Test
        @DisplayName("Boolean matrix multiplication C = A x B")
        void booleanMultiplication() {
            // A (2x3):
            // 1 0 1
            // 0 1 0
            BitMatrix a = BitMatrix.fromBinaryStrings(
                    "101",
                    "010"
            );

            // B (3x2):
            // 0 1
            // 1 0
            // 1 1
            BitMatrix b = BitMatrix.fromBinaryStrings(
                    "01",
                    "10",
                    "11"
            );

            // C[0,0] = (1&0) | (0&1) | (1&1) = 1
            // C[0,1] = (1&1) | (0&0) | (1&1) = 1
            // C[1,0] = (0&0) | (1&1) | (0&1) = 1
            // C[1,1] = (0&1) | (1&0) | (0&1) = 0
            BitMatrix c = a.multiply(b);
            assertEquals(2, c.rows());
            assertEquals(2, c.cols());
            assertEquals("11\n10", c.toBinaryString());
        }

        @Test
        @DisplayName("Transposition M^T")
        void transposition() {
            BitMatrix m = BitMatrix.fromBinaryStrings(
                    "110",
                    "001"
            );
            BitMatrix t = m.transpose();

            assertEquals(3, t.rows());
            assertEquals(2, t.cols());
            assertEquals("10\n10\n01", t.toBinaryString());
        }

        @Test
        @DisplayName("Warshall Transitive Closure computes full graph reachability")
        void transitiveClosure() {
            // Directed graph:
            // 0 -> 1
            // 1 -> 2
            // 2 -> 3
            // 3 -> (none)
            BitMatrix adj = new BitMatrix(4, 4);
            adj.set(0, 1, true);
            adj.set(1, 2, true);
            adj.set(2, 3, true);

            BitMatrix tc = adj.transitiveClosure();

            // 0 can reach 1, 2, 3
            assertTrue(tc.get(0, 1));
            assertTrue(tc.get(0, 2));
            assertTrue(tc.get(0, 3));
            assertFalse(tc.get(0, 0));

            // 1 can reach 2, 3
            assertTrue(tc.get(1, 2));
            assertTrue(tc.get(1, 3));
            assertFalse(tc.get(1, 0));

            // 2 can reach 3
            assertTrue(tc.get(2, 3));

            // 3 cannot reach anything
            assertEquals(0, tc.rowCardinality(3));
        }
    }

    @Nested
    @DisplayName("Array Conversion, Bounds, and Equality")
    class ConversionAndBoundsTests {

        @Test
        @DisplayName("Converts to and from boolean 2D array")
        void arrayConversion() {
            boolean[][] arr = {
                    {true, false, true},
                    {false, true, false}
            };
            BitMatrix m = BitMatrix.fromArray(arr);
            assertArrayEquals(arr, m.toArray());
            assertEquals(m, m.copy());
            assertEquals(m.hashCode(), m.copy().hashCode());
        }

        @Test
        @DisplayName("Throws exception on invalid bounds")
        void boundsChecking() {
            BitMatrix m = new BitMatrix(3, 3);
            assertThrows(IndexOutOfBoundsException.class, () -> m.get(-1, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> m.get(3, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> m.get(0, 3));
            assertThrows(IndexOutOfBoundsException.class, () -> m.set(3, 0, true));
        }

        @Test
        @DisplayName("Throws on invalid dimensions")
        void invalidDimensions() {
            assertThrows(IllegalArgumentException.class, () -> new BitMatrix(0, 5));
            assertThrows(IllegalArgumentException.class, () -> new BitMatrix(5, -1));
        }
    }
}
