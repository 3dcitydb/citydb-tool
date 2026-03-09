/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.util.tiling;

import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONType;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Envelope;
import org.citydb.util.tiling.options.DimensionScheme;
import org.citydb.util.tiling.options.MatrixScheme;

@JSONType(serializeFeatures = JSONWriter.Feature.WriteClassName,
        typeKey = "type",
        seeAlso = {DimensionScheme.class, MatrixScheme.class},
        seeAlsoDefault = MatrixScheme.class)
public abstract class TilingScheme {
    protected abstract TileMatrix buildTileMatrix(Envelope extent, DatabaseAdapter adapter) throws TilingException;

    protected TileMatrix buildTileMatrix(Coordinate lowerCorner, Coordinate upperCorner, int columns, int rows,
                                         double tileWidth, double tileHeight, DatabaseAdapter adapter) {
        return new TileMatrix(lowerCorner, upperCorner, columns, rows, tileWidth, tileHeight, adapter);
    }
}
