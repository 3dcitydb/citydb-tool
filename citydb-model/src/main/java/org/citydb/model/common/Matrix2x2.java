/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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

package org.citydb.model.common;

import org.citydb.model.util.matrix.Matrix;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Matrix2x2 extends Matrix {

    private Matrix2x2() {
        super(2, 2);
    }

    private Matrix2x2(Matrix matrix) {
        super(matrix.getElements(), 2, 2);
    }

    public static Matrix2x2 newInstance() {
        return new Matrix2x2();
    }

    public static Matrix2x2 of(Matrix matrix) {
        Objects.requireNonNull(matrix, "The matrix must not be null.");
        int rows = matrix.getRows();
        int columns = matrix.getColumns();
        return new Matrix2x2(rows != 2 || columns != 2 ?
                Matrix.identity(2, 2).setSubMatrix(0, Math.min(rows, 2) - 1, 0, Math.min(columns, 2) - 1, matrix) :
                matrix);
    }

    public static Matrix2x2 ofRowMajor(List<Double> values) {
        Objects.requireNonNull(values, "The matrix values must not be null.");
        if (values.size() > 3) {
            return new Matrix2x2(new Matrix(values.subList(0, 4), 2));
        } else {
            throw new IllegalArgumentException("A 2x2 matrix requires 4 values.");
        }
    }

    public static Matrix2x2 ofRowMajor(double... values) {
        return ofRowMajor(values != null ? Arrays.stream(values).boxed().toList() : null);
    }

    public static Matrix2x2 identity() {
        return new Matrix2x2(Matrix.identity(2, 2));
    }
}
