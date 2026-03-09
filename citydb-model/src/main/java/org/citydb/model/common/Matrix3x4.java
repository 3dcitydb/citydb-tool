/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.common;

import org.citydb.model.util.matrix.Matrix;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Matrix3x4 extends Matrix {

    private Matrix3x4() {
        super(3, 4);
    }

    private Matrix3x4(Matrix matrix) {
        super(matrix.getElements(), 3, 4);
    }

    public static Matrix3x4 newInstance() {
        return new Matrix3x4();
    }

    public static Matrix3x4 of(Matrix matrix) {
        Objects.requireNonNull(matrix, "The matrix must not be null.");
        int rows = matrix.getRows();
        int columns = matrix.getColumns();
        return new Matrix3x4(rows != 3 || columns != 4 ?
                Matrix.identity(4, 4)
                        .setSubMatrix(0, Math.min(rows, 3) - 1, 0, Math.min(columns, 4) - 1, matrix)
                        .getSubMatrix(0, 2, 0, 3) :
                matrix);
    }

    public static Matrix3x4 ofRowMajor(List<Double> values) {
        Objects.requireNonNull(values, "The matrix values must not be null.");
        if (values.size() > 11) {
            return new Matrix3x4(new Matrix(values.subList(0, 12), 3));
        } else {
            throw new IllegalArgumentException("A 3x4 matrix requires 12 values.");
        }
    }

    public static Matrix3x4 ofRowMajor(double... values) {
        return ofRowMajor(values != null ? Arrays.stream(values).boxed().toList() : null);
    }

    public static Matrix3x4 identity() {
        return new Matrix3x4(Matrix.identity(3, 4));
    }
}
