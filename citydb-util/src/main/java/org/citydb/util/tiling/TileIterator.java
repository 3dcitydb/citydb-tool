/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.util.tiling;

import org.citydb.util.tiling.options.TileMatrixOrigin;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class TileIterator implements Iterator<Tile> {
    private final TileMatrix matrix;
    private final TileMatrixOrigin origin;
    private int column = -1;
    private int row;
    private boolean hasNext;

    TileIterator(TileMatrix matrix, TileMatrixOrigin origin) {
        this.matrix = matrix;
        this.origin = origin;
    }

    @Override
    public boolean hasNext() {
        if (!hasNext) {
            if (++column == matrix.getColumns()) {
                column = 0;
                row++;
            }

            hasNext = row < matrix.getRows();
        }

        return hasNext;
    }

    @Override
    public Tile next() {
        try {
            if (hasNext()) {
                return matrix.getTileAt(column, row, origin);
            } else {
                throw new NoSuchElementException();
            }
        } finally {
            hasNext = false;
        }
    }
}
