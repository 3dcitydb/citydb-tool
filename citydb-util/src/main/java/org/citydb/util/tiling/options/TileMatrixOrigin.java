/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.util.tiling.options;

public enum TileMatrixOrigin {
    TOP_LEFT("topLeft"),
    BOTTOM_LEFT("bottomLeft");

    private final String value;

    TileMatrixOrigin(String value) {
        this.value = value;
    }

    public String toValue() {
        return value;
    }

    public static TileMatrixOrigin fromValue(String value) {
        for (TileMatrixOrigin v : TileMatrixOrigin.values()) {
            if (v.value.equals(value)) {
                return v;
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return value;
    }
}
