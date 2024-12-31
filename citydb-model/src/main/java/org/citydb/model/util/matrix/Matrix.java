/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citydb.model.util.matrix;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Matrix implements Serializable {
    final double[][] elements;
    final int rows, columns;

    Matrix(double[][] elements, int rows, int columns) {
        this.elements = elements;
        this.rows = rows;
        this.columns = columns;
    }

    public Matrix(int rows, int columns) {
        this(new double[rows][columns], rows, columns);
    }

    public Matrix(Matrix other) {
        this(other.rows, other.columns);
        for (int i = 0; i < rows; ++i) {
            System.arraycopy(other.elements[i], 0, this.elements[i], 0, columns);
        }
    }

    public Matrix(double[][] elements) {
        for (int i = 1; i < elements.length; i++) {
            if (elements[i].length != elements[0].length) {
                throw new IllegalArgumentException("All rows must have the same length.");
            }
        }

        rows = elements.length;
        columns = elements[0].length;
        this.elements = elements;
    }

    public Matrix(double[] rowMajor, int rows) {
        this.rows = rows;
        columns = rows != 0 ? rowMajor.length / rows : 0;
        if (rows * columns != rowMajor.length) {
            throw new IllegalArgumentException("Array length must be a multiple of m.");
        }

        elements = new double[rows][columns];
        for (int i = 0; i < rows; i++) {
            System.arraycopy(rowMajor, i * columns, elements[i], 0, columns);
        }
    }

    public static Matrix identity(int rows, int columns) {
        Matrix matrix = new Matrix(rows, columns);
        for (int i = 0; i < rows; i++) {
            matrix.elements[i][i] = 1;
        }

        return matrix;
    }

    public double[][] getElements() {
        return elements;
    }

    public List<Double> getColumnMajor() {
        List<Double> values = new ArrayList<>(rows * columns);
        for (int i = 0; i < columns; ++i) {
            for (int j = 0; j < rows; ++j) {
                values.add(elements[j][i]);
            }
        }

        return values;
    }

    public List<Double> getRowMajor() {
        List<Double> values = new ArrayList<>(rows * columns);
        for (double[] row : elements) {
            for (double value : row) {
                values.add(value);
            }
        }

        return values;
    }

    public int getRows() {
        return rows;
    }

    public int getColumns() {
        return columns;
    }

    public double get(int row, int column) {
        return elements[row][column];
    }

    public void set(int row, int column, double value) {
        elements[row][column] = value;
    }

    public Matrix getSubMatrix(int rowStart, int rowEnd, int columnStart, int columnEnd) {
        Matrix result = new Matrix(rowEnd - rowStart + 1, columnEnd - columnStart + 1);
        for (int i = rowStart; i <= rowEnd; i++) {
            System.arraycopy(elements[i], columnStart, result.elements[i - rowStart], 0, columnEnd - columnStart + 1);
        }

        return result;
    }

    Matrix getSubMatrix(int[] rowIndices, int columnStart, int columnEnd) {
        Matrix result = new Matrix(rowIndices.length, columnEnd - columnStart + 1);
        for (int i = 0; i < rowIndices.length; i++) {
            System.arraycopy(elements[rowIndices[i]], columnStart, result.elements[i], 0, columnEnd - columnStart + 1);
        }

        return result;
    }

    public void setSubMatrix(int rowStart, int rowEnd, int columnStart, int columnEnd, Matrix matrix) {
        for (int i = rowStart; i <= rowEnd; i++) {
            System.arraycopy(matrix.elements[i - rowStart], 0, elements[i], columnStart, columnEnd - columnStart + 1);
        }
    }

    public Matrix transpose() {
        Matrix result = new Matrix(columns, rows);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                result.elements[j][i] = elements[i][j];
            }
        }

        return result;
    }

    public Matrix uminus() {
        Matrix result = new Matrix(rows, columns);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                result.elements[i][j] = -elements[i][j];
            }
        }

        return result;
    }

    public Matrix plus(Matrix matrix) {
        checkDimensions(matrix);
        Matrix result = new Matrix(rows, columns);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                result.elements[i][j] = elements[i][j] + matrix.elements[i][j];
            }
        }

        return result;
    }

    public Matrix plusEquals(Matrix matrix) {
        checkDimensions(matrix);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                elements[i][j] += matrix.elements[i][j];
            }
        }

        return this;
    }

    public Matrix minus(Matrix matrix) {
        checkDimensions(matrix);
        Matrix result = new Matrix(rows, columns);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                result.elements[i][j] = elements[i][j] - matrix.elements[i][j];
            }
        }

        return result;
    }

    public Matrix minusEquals(Matrix matrix) {
        checkDimensions(matrix);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                elements[i][j] -= matrix.elements[i][j];
            }
        }

        return this;
    }

    public Matrix multiply(Matrix matrix) {
        checkDimensions(matrix);
        Matrix result = new Matrix(rows, columns);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                result.elements[i][j] = elements[i][j] * matrix.elements[i][j];
            }
        }

        return result;
    }

    public Matrix multiplyEquals(Matrix matrix) {
        checkDimensions(matrix);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                elements[i][j] *= matrix.elements[i][j];
            }
        }

        return this;
    }

    public Matrix rightDivide(Matrix matrix) {
        checkDimensions(matrix);
        Matrix result = new Matrix(rows, columns);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                result.elements[i][j] = elements[i][j] / matrix.elements[i][j];
            }
        }

        return result;
    }

    public Matrix rightDivideEquals(Matrix matrix) {
        checkDimensions(matrix);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                elements[i][j] /= matrix.elements[i][j];
            }
        }

        return this;
    }

    public Matrix leftDivide(Matrix matrix) {
        checkDimensions(matrix);
        Matrix result = new Matrix(rows, columns);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                result.elements[i][j] = matrix.elements[i][j] / elements[i][j];
            }
        }

        return result;
    }

    public Matrix leftDivideEquals(Matrix matrix) {
        checkDimensions(matrix);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                elements[i][j] = matrix.elements[i][j] / elements[i][j];
            }
        }

        return this;
    }

    public Matrix times(double value) {
        Matrix result = new Matrix(rows, columns);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                result.elements[i][j] = value * elements[i][j];
            }
        }

        return result;
    }

    public Matrix timesEquals(double value) {
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < columns; j++) {
                elements[i][j] *= value;
            }
        }

        return this;
    }

    public Matrix times(Matrix matrix) {
        if (matrix.rows != columns) {
            throw new IllegalArgumentException("Matrix inner dimensions must agree.");
        }

        Matrix result = new Matrix(rows, matrix.columns);
        for (int j = 0; j < matrix.columns; j++) {
            for (int i = 0; i < rows; i++) {
                double value = 0;
                for (int k = 0; k < columns; k++) {
                    value += elements[i][k] * matrix.elements[k][j];
                }

                result.elements[i][j] = value;
            }
        }

        return result;
    }

    public Matrix solve(Matrix matrix) {
        return rows == columns ?
                new LUDecomposition(this).solve(matrix) :
                new QRDecomposition(this).solve(matrix);
    }

    public Matrix invert() {
        return solve(identity(rows, rows));
    }

    public Matrix copy() {
        return new Matrix(this);
    }

    public boolean isEqual(Matrix matrix) {
        if (matrix.rows == rows && matrix.columns == columns) {
            for (int i = 0; i < rows; i++) {
                if (!Arrays.equals(elements[i], matrix.elements[i])) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    private void checkDimensions(Matrix matrix) {
        if (matrix.rows != rows || matrix.columns != columns) {
            throw new IllegalArgumentException("Matrix dimensions must agree.");
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj instanceof Matrix matrix
                && matrix.rows == rows
                && matrix.columns == columns) {
            for (int i = 0; i < rows; i++) {
                if (!Arrays.equals(elements[i], matrix.elements[i])) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }
}
