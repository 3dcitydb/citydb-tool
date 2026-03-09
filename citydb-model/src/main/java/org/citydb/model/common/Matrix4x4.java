/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.common;

import org.citydb.model.util.matrix.Matrix;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Matrix4x4 extends Matrix {

    private Matrix4x4() {
        super(4, 4);
    }

    private Matrix4x4(Matrix matrix) {
        super(matrix.getElements(), 4, 4);
    }

    public static Matrix4x4 newInstance() {
        return new Matrix4x4();
    }

    public static Matrix4x4 of(Matrix matrix) {
        Objects.requireNonNull(matrix, "The matrix must not be null.");
        int rows = matrix.getRows();
        int columns = matrix.getColumns();
        return new Matrix4x4(rows != 4 || columns != 4 ?
                Matrix.identity(4, 4).setSubMatrix(0, Math.min(rows, 4) - 1, 0, Math.min(columns, 4) - 1, matrix) :
                matrix);
    }

    public static Matrix4x4 ofRowMajor(List<Double> values) {
        Objects.requireNonNull(values, "The matrix values must not be null.");
        if (values.size() > 15) {
            return new Matrix4x4(new Matrix(values.subList(0, 16), 4));
        } else {
            throw new IllegalArgumentException("A 4x4 matrix requires 16 values.");
        }
    }

    public static Matrix4x4 ofRowMajor(double... values) {
        return ofRowMajor(values != null ? Arrays.stream(values).boxed().toList() : null);
    }

    public static Matrix4x4 identity() {
        return new Matrix4x4(Matrix.identity(4, 4));
    }
}
