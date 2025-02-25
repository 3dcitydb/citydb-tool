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
