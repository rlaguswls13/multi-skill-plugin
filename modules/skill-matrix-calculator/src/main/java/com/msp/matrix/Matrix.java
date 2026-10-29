package com.msp.matrix;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable 2D matrix linear algebra calculator.
 * <p>
 * Supports fundamental matrix arithmetic, scalar operations, transposition, trace,
 * determinant calculation, and inversion with verification.
 */
public final class Matrix {

    private final int rows;
    private final int cols;
    private final double[][] data;

    /**
     * Constructs a matrix with defensive copy of the given 2D array.
     *
     * @param values 2D array of matrix values
     */
    public Matrix(double[][] values) {
        Objects.requireNonNull(values, "values cannot be null");
        if (values.length == 0 || values[0].length == 0) {
            throw new IllegalArgumentException("Matrix dimensions must be non-zero");
        }
        this.rows = values.length;
        this.cols = values[0].length;
        this.data = new double[rows][cols];

        for (int i = 0; i < rows; i++) {
            if (values[i] == null || values[i].length != cols) {
                throw new IllegalArgumentException("All rows must have identical column count: " + cols);
            }
            System.arraycopy(values[i], 0, this.data[i], 0, cols);
        }
    }

    /**
     * Creates a matrix from 2D double array.
     */
    public static Matrix of(double[][] values) {
        return new Matrix(values);
    }

    /**
     * Creates a zero matrix of dimension rows x cols.
     */
    public static Matrix zeros(int rows, int cols) {
        if (rows <= 0 || cols <= 0) {
            throw new IllegalArgumentException("Rows and cols must be positive: " + rows + "x" + cols);
        }
        return new Matrix(new double[rows][cols]);
    }

    /**
     * Creates an n x n identity matrix.
     */
    public static Matrix identity(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be positive: " + size);
        }
        double[][] identityData = new double[size][size];
        for (int i = 0; i < size; i++) {
            identityData[i][i] = 1.0;
        }
        return new Matrix(identityData);
    }

    /**
     * Creates a matrix from flat elements in row-major order.
     */
    public static Matrix from(int rows, int cols, double... values) {
        if (rows <= 0 || cols <= 0) {
            throw new IllegalArgumentException("Dimensions must be positive: " + rows + "x" + cols);
        }
        Objects.requireNonNull(values, "values cannot be null");
        if (values.length != rows * cols) {
            throw new IllegalArgumentException(
                    "Element count " + values.length + " does not match matrix dimensions " + rows + "x" + cols);
        }
        double[][] data = new double[rows][cols];
        for (int r = 0; r < rows; r++) {
            System.arraycopy(values, r * cols, data[r], 0, cols);
        }
        return new Matrix(data);
    }

    public int rows() {
        return rows;
    }

    public int cols() {
        return cols;
    }

    public double get(int row, int col) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) {
            throw new IndexOutOfBoundsException(
                    "Index [" + row + ", " + col + "] out of bounds for " + rows + "x" + cols + " matrix");
        }
        return data[row][col];
    }

    public boolean isSquare() {
        return rows == cols;
    }

    /**
     * Matrix addition: this + other.
     */
    public Matrix add(Matrix other) {
        Objects.requireNonNull(other, "other matrix cannot be null");
        if (this.rows != other.rows || this.cols != other.cols) {
            throw new IllegalArgumentException(
                    "Cannot add matrices with differing dimensions: " + rows + "x" + cols + " vs " + other.rows + "x" + other.cols);
        }
        double[][] result = new double[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                result[r][c] = this.data[r][c] + other.data[r][c];
            }
        }
        return new Matrix(result);
    }

    /**
     * Matrix subtraction: this - other.
     */
    public Matrix subtract(Matrix other) {
        Objects.requireNonNull(other, "other matrix cannot be null");
        if (this.rows != other.rows || this.cols != other.cols) {
            throw new IllegalArgumentException(
                    "Cannot subtract matrices with differing dimensions: " + rows + "x" + cols + " vs " + other.rows + "x" + other.cols);
        }
        double[][] result = new double[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                result[r][c] = this.data[r][c] - other.data[r][c];
            }
        }
        return new Matrix(result);
    }

    /**
     * Matrix multiplication: this * other.
     */
    public Matrix multiply(Matrix other) {
        Objects.requireNonNull(other, "other matrix cannot be null");
        if (this.cols != other.rows) {
            throw new IllegalArgumentException(
                    "Cannot multiply matrices: inner dimensions mismatch (" + this.rows + "x" + this.cols + ") * (" + other.rows + "x" + other.cols + ")");
        }
        int resultRows = this.rows;
        int resultCols = other.cols;
        double[][] result = new double[resultRows][resultCols];

        for (int i = 0; i < resultRows; i++) {
            for (int k = 0; k < this.cols; k++) {
                double a = this.data[i][k];
                for (int j = 0; j < resultCols; j++) {
                    result[i][j] += a * other.data[k][j];
                }
            }
        }
        return new Matrix(result);
    }

    /**
     * Scalar multiplication: this * scalar.
     */
    public Matrix multiply(double scalar) {
        double[][] result = new double[rows][cols];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                result[r][c] = this.data[r][c] * scalar;
            }
        }
        return new Matrix(result);
    }

    /**
     * Transposes this matrix.
     */
    public Matrix transpose() {
        double[][] result = new double[cols][rows];
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                result[c][r] = this.data[r][c];
            }
        }
        return new Matrix(result);
    }

    /**
     * Computes the trace (sum of diagonal elements) of a square matrix.
     */
    public double trace() {
        if (!isSquare()) {
            throw new IllegalStateException("Trace is only defined for square matrices, got: " + rows + "x" + cols);
        }
        double sum = 0.0;
        for (int i = 0; i < rows; i++) {
            sum += data[i][i];
        }
        return sum;
    }

    /**
     * Computes the determinant of a square matrix.
     */
    public double determinant() {
        if (!isSquare()) {
            throw new IllegalStateException("Determinant is only defined for square matrices, got: " + rows + "x" + cols);
        }

        if (rows == 1) {
            return data[0][0];
        }
        if (rows == 2) {
            return data[0][0] * data[1][1] - data[0][1] * data[1][0];
        }
        if (rows == 3) {
            double a = data[0][0], b = data[0][1], c = data[0][2];
            double d = data[1][0], e = data[1][1], f = data[1][2];
            double g = data[2][0], h = data[2][1], i = data[2][2];
            return a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g);
        }

        // Gaussian elimination with partial pivoting for NxN matrices
        double[][] mat = toArray();
        int n = rows;
        double det = 1.0;

        for (int i = 0; i < n; i++) {
            int pivot = i;
            for (int j = i + 1; j < n; j++) {
                if (Math.abs(mat[j][i]) > Math.abs(mat[pivot][i])) {
                    pivot = j;
                }
            }

            if (Math.abs(mat[pivot][i]) < 1e-12) {
                return 0.0;
            }

            if (pivot != i) {
                double[] temp = mat[i];
                mat[i] = mat[pivot];
                mat[pivot] = temp;
                det = -det;
            }

            det *= mat[i][i];
            for (int j = i + 1; j < n; j++) {
                double factor = mat[j][i] / mat[i][i];
                for (int k = i + 1; k < n; k++) {
                    mat[j][k] -= factor * mat[i][k];
                }
            }
        }
        return det;
    }

    /**
     * Computes the matrix inverse: $A^{-1}$.
     *
     * @return inverted matrix
     * @throws ArithmeticException if matrix is singular (determinant is 0)
     * @throws IllegalStateException if matrix is not square
     */
    public Matrix inverse() {
        if (!isSquare()) {
            throw new IllegalStateException("Inverse is only defined for square matrices, got: " + rows + "x" + cols);
        }

        double det = determinant();
        if (Math.abs(det) < 1e-12) {
            throw new ArithmeticException("Matrix is singular (determinant is zero) and cannot be inverted");
        }

        if (rows == 1) {
            return new Matrix(new double[][]{{1.0 / det}});
        }

        if (rows == 2) {
            double a = data[0][0], b = data[0][1];
            double c = data[1][0], d = data[1][1];
            return new Matrix(new double[][]{
                    {d / det, -b / det},
                    {-c / det, a / det}
            });
        }

        if (rows == 3) {
            double a = data[0][0], b = data[0][1], c = data[0][2];
            double d = data[1][0], e = data[1][1], f = data[1][2];
            double g = data[2][0], h = data[2][1], i = data[2][2];

            double inv00 = (e * i - f * h) / det;
            double inv01 = (c * h - b * i) / det;
            double inv02 = (b * f - c * e) / det;

            double inv10 = (f * g - d * i) / det;
            double inv11 = (a * i - c * g) / det;
            double inv12 = (c * d - a * f) / det;

            double inv20 = (d * h - e * g) / det;
            double inv21 = (g * b - a * h) / det;
            double inv22 = (a * e - b * d) / det;

            return new Matrix(new double[][]{
                    {inv00, inv01, inv02},
                    {inv10, inv11, inv12},
                    {inv20, inv21, inv22}
            });
        }

        // Gauss-Jordan elimination on augmented matrix [A | I]
        int n = rows;
        double[][] aug = new double[n][2 * n];
        for (int r = 0; r < n; r++) {
            System.arraycopy(data[r], 0, aug[r], 0, n);
            aug[r][n + r] = 1.0;
        }

        for (int i = 0; i < n; i++) {
            int pivot = i;
            for (int j = i + 1; j < n; j++) {
                if (Math.abs(aug[j][i]) > Math.abs(aug[pivot][i])) {
                    pivot = j;
                }
            }

            if (Math.abs(aug[pivot][i]) < 1e-12) {
                throw new ArithmeticException("Matrix is singular and cannot be inverted");
            }

            if (pivot != i) {
                double[] temp = aug[i];
                aug[i] = aug[pivot];
                aug[pivot] = temp;
            }

            double pivotVal = aug[i][i];
            for (int k = 0; k < 2 * n; k++) {
                aug[i][k] /= pivotVal;
            }

            for (int j = 0; j < n; j++) {
                if (j != i) {
                    double factor = aug[j][i];
                    for (int k = 0; k < 2 * n; k++) {
                        aug[j][k] -= factor * aug[i][k];
                    }
                }
            }
        }

        double[][] invData = new double[n][n];
        for (int r = 0; r < n; r++) {
            System.arraycopy(aug[r], n, invData[r], 0, n);
        }
        return new Matrix(invData);
    }

    /**
     * Compares two matrices for approximate equality within a tolerance epsilon.
     */
    public boolean equals(Matrix other, double epsilon) {
        if (other == null || this.rows != other.rows || this.cols != other.cols) {
            return false;
        }
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (Math.abs(this.data[r][c] - other.data[r][c]) > epsilon) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Returns a deep defensive copy of the internal array data.
     */
    public double[][] toArray() {
        double[][] copy = new double[rows][cols];
        for (int r = 0; r < rows; r++) {
            System.arraycopy(data[r], 0, copy[r], 0, cols);
        }
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Matrix matrix)) return false;
        if (rows != matrix.rows || cols != matrix.cols) return false;
        for (int r = 0; r < rows; r++) {
            if (!Arrays.equals(data[r], matrix.data[r])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(rows, cols);
        for (double[] row : data) {
            result = 31 * result + Arrays.hashCode(row);
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Matrix ").append(rows).append("x").append(cols).append(":\n");
        for (int r = 0; r < rows; r++) {
            sb.append("  [");
            for (int c = 0; c < cols; c++) {
                sb.append(String.format("%8.4f", data[r][c]));
                if (c < cols - 1) sb.append(", ");
            }
            sb.append("]\n");
        }
        return sb.toString();
    }
}
