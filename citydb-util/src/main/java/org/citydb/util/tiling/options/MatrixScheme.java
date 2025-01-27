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
