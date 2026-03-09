/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.util.tiling.options;

import com.alibaba.fastjson2.annotation.JSONType;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.model.geometry.Envelope;
import org.citydb.util.tiling.TileMatrix;
import org.citydb.util.tiling.TilingException;
import org.citydb.util.tiling.TilingScheme;

@JSONType(typeName = "Matrix")
public class MatrixScheme extends TilingScheme {
    private int columns = 1;
    private int rows = 1;

    public static MatrixScheme of(int columns, int rows) {
        return new MatrixScheme().setColumns(columns).setRows(rows);
    }

    public int getColumns() {
        return columns;
    }

    public MatrixScheme setColumns(int columns) {
        this.columns = columns;
        return this;
    }

    public int getRows() {
        return rows;
    }

    public MatrixScheme setRows(int rows) {
        this.rows = rows;
        return this;
    }

    @Override
    protected TileMatrix buildTileMatrix(Envelope extent, DatabaseAdapter adapter) throws TilingException {
        if (columns < 1) {
            throw new TilingException("The number of columns must be a positive integer but was " + columns + ".");
        } else if (rows < 1) {
            throw new TilingException("The number of rows must be a positive integer but was " + rows + ".");
        }

        return buildTileMatrix(extent.getLowerCorner(), extent.getUpperCorner(), columns, rows,
                (extent.getUpperCorner().getX() - extent.getLowerCorner().getX()) / columns,
                (extent.getUpperCorner().getY() - extent.getLowerCorner().getY()) / rows,
                adapter);
    }
}
