/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.util.matrix;

public class QRDecomposition {
    private final double[][] elements;
    private final int rows, columns;
    private final double[] diag;

    QRDecomposition(Matrix A) {
        elements = A.copy().elements;
        rows = A.rows;
        columns = A.columns;
        diag = new double[columns];

        for (int k = 0; k < columns; k++) {
            double nrm = 0;
            for (int i = k; i < rows; i++) {
                nrm = hypot(nrm, elements[i][k]);
            }

            if (nrm != 0) {
                if (elements[k][k] < 0) {
                    nrm = -nrm;
                }

                for (int i = k; i < rows; i++) {
                    elements[i][k] /= nrm;
                }

                elements[k][k] += 1;
                for (int j = k + 1; j < columns; j++) {
                    double s = 0;
                    for (int i = k; i < rows; i++) {
                        s += elements[i][k] * elements[i][j];
                    }

                    s = -s / elements[k][k];
                    for (int i = k; i < rows; i++) {
                        elements[i][j] += s * elements[i][k];
                    }
                }
            }

            diag[k] = -nrm;
        }
    }

    Matrix solve(Matrix matrix) {
        if (matrix.rows != rows) {
            throw new IllegalArgumentException("Matrix row dimensions must agree.");
        } else if (!isFullRank()) {
            throw new RuntimeException("Matrix is rank deficient.");
        }

        double[][] result = matrix.copy().elements;
        for (int k = 0; k < columns; k++) {
            for (int j = 0; j < matrix.columns; j++) {
                double s = 0;
                for (int i = k; i < rows; i++) {
                    s += elements[i][k] * result[i][j];
                }

                s = -s / elements[k][k];
                for (int i = k; i < rows; i++) {
                    result[i][j] += s * elements[i][k];
                }
            }
        }

        for (int k = columns - 1; k >= 0; k--) {
            for (int j = 0; j < matrix.columns; j++) {
                result[k][j] /= diag[k];
            }

            for (int i = 0; i < k; i++) {
                for (int j = 0; j < matrix.columns; j++) {
                    result[i][j] -= result[k][j] * elements[i][k];
                }
            }
        }

        return new Matrix(result, columns, matrix.columns).getSubMatrix(0, columns - 1, 0, matrix.columns - 1);
    }

    private boolean isFullRank() {
        for (int j = 0; j < columns; j++) {
            if (diag[j] == 0) {
                return false;
            }
        }

        return true;
    }

    private double hypot(double a, double b) {
        if (Math.abs(a) > Math.abs(b)) {
            double r = b / a;
            return Math.abs(a) * Math.sqrt(1 + r * r);
        } else if (b != 0) {
            double r = a / b;
            return Math.abs(b) * Math.sqrt(1 + r * r);
        } else {
            return 0;
        }
    }
}
