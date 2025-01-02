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

    public static Matrix4x4 identity() {
        return new Matrix4x4(Matrix.identity(4, 4));
    }
}
