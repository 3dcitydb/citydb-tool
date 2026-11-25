/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.util.tiling;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Envelope;
import org.citydb.util.tiling.options.DimensionScheme;
import org.citydb.util.tiling.options.MatrixScheme;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = MatrixScheme.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DimensionScheme.class, name = "Dimension"),
        @JsonSubTypes.Type(value = MatrixScheme.class, name = "Matrix")
})
public abstract class TilingScheme {
    protected abstract TileMatrix buildTileMatrix(Envelope extent, DatabaseAdapter adapter) throws TilingException;

    protected TileMatrix buildTileMatrix(Coordinate lowerCorner, Coordinate upperCorner, int columns, int rows,
                                         double tileWidth, double tileHeight, DatabaseAdapter adapter) {
        return new TileMatrix(lowerCorner, upperCorner, columns, rows, tileWidth, tileHeight, adapter);
    }
}
