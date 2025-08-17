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

package org.citydb.model.util.matrix;

public class LUDecomposition {
    private final double[][] elements;
    private final int rows, columns;
    private final int[] piv;

    LUDecomposition(Matrix matrix) {
        elements = matrix.copy().elements;
        rows = matrix.rows;
        columns = matrix.columns;

        piv = new int[rows];
        for (int i = 0; i < rows; i++) {
            piv[i] = i;
        }

        int pivsign = 1;
        double[] column = new double[rows];

        for (int j = 0; j < columns; j++) {
            for (int i = 0; i < rows; i++) {
                column[i] = elements[i][j];
            }

            for (int i = 0; i < rows; i++) {
                int kmax = Math.min(i, j);
                double s = 0;
                for (int k = 0; k < kmax; k++) {
                    s += elements[i][k] * column[k];
                }

                elements[i][j] = column[i] -= s;
            }

            int p = j;
            for (int i = j + 1; i < rows; i++) {
                if (Math.abs(column[i]) > Math.abs(column[p])) {
                    p = i;
                }
            }

            if (p != j) {
                for (int k = 0; k < columns; k++) {
                    double t = elements[p][k];
                    elements[p][k] = elements[j][k];
                    elements[j][k] = t;
                }

                int k = piv[p];
                piv[p] = piv[j];
                piv[j] = k;
                pivsign = -pivsign;
            }

            if (j < rows & elements[j][j] != 0) {
                for (int i = j + 1; i < rows; i++) {
                    elements[i][j] /= elements[j][j];
                }
            }
        }
    }

    Matrix solve(Matrix matrix) {
        if (matrix.rows != rows) {
            throw new IllegalArgumentException("Matrix row dimensions must agree.");
        } else if (!isNonSingular()) {
            throw new RuntimeException("Matrix is singular.");
        }

        Matrix result = matrix.getSubMatrix(piv, 0, matrix.columns - 1);
        for (int k = 0; k < columns; k++) {
            for (int i = k + 1; i < columns; i++) {
                for (int j = 0; j < matrix.columns; j++) {
                    result.elements[i][j] -= result.elements[k][j] * elements[i][k];
                }
            }
        }

        for (int k = columns - 1; k >= 0; k--) {
            for (int j = 0; j < matrix.columns; j++) {
                result.elements[k][j] /= elements[k][k];
            }

            for (int i = 0; i < k; i++) {
                for (int j = 0; j < matrix.columns; j++) {
                    result.elements[i][j] -= result.elements[k][j] * elements[i][k];
                }
            }
        }

        return result;
    }

    private boolean isNonSingular() {
        for (int j = 0; j < columns; j++) {
            if (elements[j][j] == 0) {
                return false;
            }
        }

        return true;
    }
}
