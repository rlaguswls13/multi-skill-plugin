package com.msp.bitmatrix;

import java.util.Arrays;
import java.util.Objects;

/**
 * High-performance, memory-efficient 2D Boolean BitMatrix backed by 64-bit {@code long[]} bitsets.
 * <p>
 * Packed representation allows $64\times$ memory reduction compared to {@code boolean[][]}
 * and enables ultra-fast word-level SIMD-like bitwise row operations (AND, OR, XOR, NOT),
 * matrix multiplication (Boolean semi-ring), and Warshall transitive closure reachability.
 */
public final class BitMatrix {

    private final int rows;
    private final int cols;
    private final int wordsPerRow;
    private final long lastWordMask;
    private final long[] words;

    /**
     * Constructs an empty BitMatrix of dimensions {@code rows} x {@code cols}.
     *
     * @param rows number of rows (must be > 0)
     * @param cols number of columns (must be > 0)
     */
    public BitMatrix(int rows, int cols) {
        if (rows <= 0) {
            throw new IllegalArgumentException("rows must be > 0, got: " + rows);
        }
        if (cols <= 0) {
            throw new IllegalArgumentException("cols must be > 0, got: " + cols);
        }
        this.rows = rows;
        this.cols = cols;
        this.wordsPerRow = (cols + 63) >>> 6;
        int rem = cols & 63;
        this.lastWordMask = (rem == 0) ? -1L : (1L << rem) - 1L;
        this.words = new long[rows * wordsPerRow];
    }

    private BitMatrix(int rows, int cols, int wordsPerRow, long lastWordMask, long[] words) {
        this.rows = rows;
        this.cols = cols;
        this.wordsPerRow = wordsPerRow;
        this.lastWordMask = lastWordMask;
        this.words = words;
    }

    /**
     * Creates a BitMatrix initialized from a 2D boolean array.
     */
    public static BitMatrix fromArray(boolean[][] array) {
        Objects.requireNonNull(array, "array must not be null");
        int r = array.length;
        if (r == 0) {
            throw new IllegalArgumentException("array must have at least 1 row");
        }
        int c = array[0].length;
        if (c == 0) {
            throw new IllegalArgumentException("array must have at least 1 column");
        }
        BitMatrix bm = new BitMatrix(r, c);
        for (int i = 0; i < r; i++) {
            if (array[i].length != c) {
                throw new IllegalArgumentException("Row " + i + " length " + array[i].length + " does not match cols " + c);
            }
            for (int j = 0; j < c; j++) {
                if (array[i][j]) {
                    bm.set(i, j, true);
                }
            }
        }
        return bm;
    }

    /**
     * Creates a BitMatrix from binary string rows (e.g. "101", "011").
     */
    public static BitMatrix fromBinaryStrings(String... rows) {
        Objects.requireNonNull(rows, "rows must not be null");
        int r = rows.length;
        if (r == 0) {
            throw new IllegalArgumentException("rows must not be empty");
        }
        int c = rows[0].length();
        if (c == 0) {
            throw new IllegalArgumentException("row string must not be empty");
        }
        BitMatrix bm = new BitMatrix(r, c);
        for (int i = 0; i < r; i++) {
            String rowStr = rows[i];
            if (rowStr.length() != c) {
                throw new IllegalArgumentException("Row " + i + " length " + rowStr.length() + " != " + c);
            }
            for (int j = 0; j < c; j++) {
                char ch = rowStr.charAt(j);
                if (ch == '1') {
                    bm.set(i, j, true);
                } else if (ch != '0') {
                    throw new IllegalArgumentException("Invalid binary character '" + ch + "' at (" + i + ", " + j + ")");
                }
            }
        }
        return bm;
    }

    /**
     * Number of rows.
     */
    public int rows() {
        return rows;
    }

    /**
     * Number of columns.
     */
    public int cols() {
        return cols;
    }

    /**
     * Gets the bit value at (row, col).
     */
    public boolean get(int row, int col) {
        checkBounds(row, col);
        int wordIdx = row * wordsPerRow + (col >>> 6);
        return (words[wordIdx] & (1L << (col & 63))) != 0L;
    }

    /**
     * Sets the bit value at (row, col).
     */
    public void set(int row, int col, boolean value) {
        checkBounds(row, col);
        int wordIdx = row * wordsPerRow + (col >>> 6);
        long bitMask = 1L << (col & 63);
        if (value) {
            words[wordIdx] |= bitMask;
        } else {
            words[wordIdx] &= ~bitMask;
        }
    }

    /**
     * Flips the bit value at (row, col).
     */
    public void flip(int row, int col) {
        checkBounds(row, col);
        int wordIdx = row * wordsPerRow + (col >>> 6);
        words[wordIdx] ^= (1L << (col & 63));
    }

    /**
     * Clears all bits in this matrix to 0.
     */
    public void clear() {
        Arrays.fill(words, 0L);
    }

    /**
     * Fills all bits in this matrix to 1.
     */
    public void fill() {
        for (int r = 0; r < rows; r++) {
            fillRow(r);
        }
    }

    /**
     * Clears all bits in a specific row.
     */
    public void clearRow(int row) {
        checkRow(row);
        int start = row * wordsPerRow;
        Arrays.fill(words, start, start + wordsPerRow, 0L);
    }

    /**
     * Fills all bits in a specific row with 1.
     */
    public void fillRow(int row) {
        checkRow(row);
        int start = row * wordsPerRow;
        for (int w = 0; w < wordsPerRow - 1; w++) {
            words[start + w] = -1L;
        }
        words[start + wordsPerRow - 1] = lastWordMask;
    }

    // In-place row operations
    /**
     * In-place row bitwise AND: {@code targetRow &= sourceRow}.
     */
    public void rowAnd(int targetRow, int sourceRow) {
        checkRow(targetRow);
        checkRow(sourceRow);
        int targetStart = targetRow * wordsPerRow;
        int sourceStart = sourceRow * wordsPerRow;
        for (int w = 0; w < wordsPerRow; w++) {
            words[targetStart + w] &= words[sourceStart + w];
        }
    }

    /**
     * In-place row bitwise OR: {@code targetRow |= sourceRow}.
     */
    public void rowOr(int targetRow, int sourceRow) {
        checkRow(targetRow);
        checkRow(sourceRow);
        int targetStart = targetRow * wordsPerRow;
        int sourceStart = sourceRow * wordsPerRow;
        for (int w = 0; w < wordsPerRow; w++) {
            words[targetStart + w] |= words[sourceStart + w];
        }
    }

    /**
     * In-place row bitwise XOR: {@code targetRow ^= sourceRow}.
     */
    public void rowXor(int targetRow, int sourceRow) {
        checkRow(targetRow);
        checkRow(sourceRow);
        int targetStart = targetRow * wordsPerRow;
        int sourceStart = sourceRow * wordsPerRow;
        for (int w = 0; w < wordsPerRow; w++) {
            words[targetStart + w] ^= words[sourceStart + w];
        }
    }

    /**
     * In-place row bitwise NOT: inverts all bits in {@code row}.
     */
    public void rowNot(int row) {
        checkRow(row);
        int start = row * wordsPerRow;
        for (int w = 0; w < wordsPerRow - 1; w++) {
            words[start + w] = ~words[start + w];
        }
        words[start + wordsPerRow - 1] = (~words[start + wordsPerRow - 1]) & lastWordMask;
    }

    /**
     * In-place row bitwise OR from another matrix's row: {@code targetRow |= other.sourceRow}.
     */
    public void rowOrFrom(int targetRow, BitMatrix other, int sourceRow) {
        checkRow(targetRow);
        other.checkRow(sourceRow);
        if (this.cols != other.cols) {
            throw new IllegalArgumentException("Column dimensions do not match: " + this.cols + " vs " + other.cols);
        }
        int targetStart = targetRow * wordsPerRow;
        int sourceStart = sourceRow * wordsPerRow;
        for (int w = 0; w < wordsPerRow; w++) {
            words[targetStart + w] |= other.words[sourceStart + w];
        }
    }

    // Matrix-level bitwise operations
    /**
     * Returns a new matrix resulting from bitwise AND with {@code other}.
     */
    public BitMatrix and(BitMatrix other) {
        BitMatrix copy = this.copy();
        copy.andInPlace(other);
        return copy;
    }

    public void andInPlace(BitMatrix other) {
        checkSameDimensions(other);
        for (int i = 0; i < words.length; i++) {
            words[i] &= other.words[i];
        }
    }

    /**
     * Returns a new matrix resulting from bitwise OR with {@code other}.
     */
    public BitMatrix or(BitMatrix other) {
        BitMatrix copy = this.copy();
        copy.orInPlace(other);
        return copy;
    }

    public void orInPlace(BitMatrix other) {
        checkSameDimensions(other);
        for (int i = 0; i < words.length; i++) {
            words[i] |= other.words[i];
        }
    }

    /**
     * Returns a new matrix resulting from bitwise XOR with {@code other}.
     */
    public BitMatrix xor(BitMatrix other) {
        BitMatrix copy = this.copy();
        copy.xorInPlace(other);
        return copy;
    }

    public void xorInPlace(BitMatrix other) {
        checkSameDimensions(other);
        for (int i = 0; i < words.length; i++) {
            words[i] ^= other.words[i];
        }
    }

    /**
     * Returns a new matrix with all bits inverted.
     */
    public BitMatrix not() {
        BitMatrix copy = this.copy();
        copy.notInPlace();
        return copy;
    }

    public void notInPlace() {
        for (int r = 0; r < rows; r++) {
            rowNot(r);
        }
    }

    /**
     * Boolean matrix multiplication (Semi-ring / Reachability): $C = A \times B$.
     * Where $A$ is $M \times K$, $B$ is $K \times N$, returning an $M \times N$ matrix.
     * <p>
     * Implemented using 64-bit word-level OR operations:
     * {@code if (A.get(i, k)) C.rowOrFrom(i, B, k)}.
     */
    public BitMatrix multiply(BitMatrix other) {
        Objects.requireNonNull(other, "other matrix must not be null");
        if (this.cols != other.rows) {
            throw new IllegalArgumentException("Multiplication dimension mismatch: ("
                    + this.rows + "x" + this.cols + ") * (" + other.rows + "x" + other.cols + ")");
        }
        BitMatrix result = new BitMatrix(this.rows, other.cols);
        for (int i = 0; i < this.rows; i++) {
            for (int k = 0; k < this.cols; k++) {
                if (this.get(i, k)) {
                    result.rowOrFrom(i, other, k);
                }
            }
        }
        return result;
    }

    /**
     * Transposes this $M \times N$ matrix into an $N \times M$ matrix ($M^T$).
     */
    public BitMatrix transpose() {
        BitMatrix result = new BitMatrix(this.cols, this.rows);
        for (int r = 0; r < this.rows; r++) {
            for (int c = 0; c < this.cols; c++) {
                if (this.get(r, c)) {
                    result.set(c, r, true);
                }
            }
        }
        return result;
    }

    /**
     * Computes the Transitive Closure (reachability) for square $V \times V$ matrices
     * using the 64-bit word-parallel Warshall algorithm ($O(V^3 / 64)$).
     */
    public BitMatrix transitiveClosure() {
        if (rows != cols) {
            throw new IllegalStateException("Transitive closure requires a square matrix, got: " + rows + "x" + cols);
        }
        BitMatrix tc = this.copy();
        for (int k = 0; k < rows; k++) {
            for (int i = 0; i < rows; i++) {
                if (tc.get(i, k)) {
                    tc.rowOr(i, k);
                }
            }
        }
        return tc;
    }

    /**
     * Total number of 1-bits in the matrix (Hamming weight / popcount).
     */
    public long cardinality() {
        long count = 0;
        for (long word : words) {
            count += Long.bitCount(word);
        }
        return count;
    }

    /**
     * Popcount of a specific row.
     */
    public int rowCardinality(int row) {
        checkRow(row);
        int start = row * wordsPerRow;
        int count = 0;
        for (int w = 0; w < wordsPerRow; w++) {
            count += Long.bitCount(words[start + w]);
        }
        return count;
    }

    /**
     * Popcount of a specific column.
     */
    public int columnCardinality(int col) {
        checkCol(col);
        int count = 0;
        for (int r = 0; r < rows; r++) {
            if (get(r, col)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Bit density of the matrix (cardinality / (rows * cols)).
     */
    public double density() {
        return (double) cardinality() / ((double) rows * cols);
    }

    /**
     * Returns true if all bits are 0.
     */
    public boolean isEmpty() {
        for (long word : words) {
            if (word != 0L) return false;
        }
        return true;
    }

    /**
     * Deep copy of this BitMatrix.
     */
    public BitMatrix copy() {
        return new BitMatrix(rows, cols, wordsPerRow, lastWordMask, Arrays.copyOf(words, words.length));
    }

    /**
     * Converts to a 2D boolean array.
     */
    public boolean[][] toArray() {
        boolean[][] result = new boolean[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                result[r][c] = get(r, c);
            }
        }
        return result;
    }

    /**
     * Formats matrix as multi-line binary string.
     */
    public String toBinaryString() {
        StringBuilder sb = new StringBuilder(rows * (cols + 1));
        for (int r = 0; r < rows; r++) {
            if (r > 0) sb.append("\n");
            for (int c = 0; c < cols; c++) {
                sb.append(get(r, c) ? '1' : '0');
            }
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BitMatrix other)) return false;
        if (rows != other.rows || cols != other.cols) return false;
        return Arrays.equals(words, other.words);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(rows, cols);
        result = 31 * result + Arrays.hashCode(words);
        return result;
    }

    @Override
    public String toString() {
        return "BitMatrix(" + rows + "x" + cols + ", cardinality=" + cardinality() + ")";
    }

    private void checkBounds(int r, int c) {
        checkRow(r);
        checkCol(c);
    }

    private void checkRow(int r) {
        if (r < 0 || r >= rows) {
            throw new IndexOutOfBoundsException("Row " + r + " out of bounds for rows=" + rows);
        }
    }

    private void checkCol(int c) {
        if (c < 0 || c >= cols) {
            throw new IndexOutOfBoundsException("Col " + c + " out of bounds for cols=" + cols);
        }
    }

    private void checkSameDimensions(BitMatrix other) {
        Objects.requireNonNull(other, "other matrix must not be null");
        if (this.rows != other.rows || this.cols != other.cols) {
            throw new IllegalArgumentException("Matrix dimensions mismatch: ("
                    + this.rows + "x" + this.cols + ") vs (" + other.rows + "x" + other.cols + ")");
        }
    }
}
