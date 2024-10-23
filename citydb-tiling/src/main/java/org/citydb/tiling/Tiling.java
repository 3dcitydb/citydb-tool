/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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

package org.citydb.tiling;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.geometry.GeometryException;
import org.citydb.database.srs.SpatialReference;
import org.citydb.database.srs.SrsException;
import org.citydb.model.geometry.Envelope;
import org.citydb.tiling.options.TileMatrixOrigin;

import java.sql.SQLException;
import java.util.Objects;

public class Tiling {
    private final Envelope extent;
    private final TileMatrix tileMatrix;
    private final TilingOptions options;

    private Tiling(Envelope extent, TileMatrix tileMatrix, TilingOptions options) {
        this.extent = extent;
        this.tileMatrix = tileMatrix;
        this.options = options;
    }

    public static Tiling of(DatabaseAdapter adapter, TilingOptions options) throws TilingException {
        Objects.requireNonNull(adapter, "The database adapter must not be null.");
        Objects.requireNonNull(options, "The tiling options must not be null.");

        Envelope extent = options.getExtent()
                .orElseThrow(() -> new TilingException("No tiling extent specified."));
        TilingScheme scheme = options.getScheme()
                .orElseThrow(() -> new TilingException("No tiling scheme specified."));

        try {
            SpatialReference reference = adapter.getGeometryAdapter().getSpatialReference(extent)
                    .orElse(adapter.getDatabaseMetadata().getSpatialReference());
            if (reference.getSRID() != adapter.getDatabaseMetadata().getSpatialReference().getSRID()) {
                extent = adapter.getGeometryAdapter().transform(extent);
            }
        } catch (GeometryException | SrsException | SQLException e) {
            throw new TilingException("Failed to transform the tiling extent to the database SRS.", e);
        }

        return new Tiling(extent, scheme.createTileMatrix(extent, adapter), options);
    }

    public Envelope getExtent() {
        return extent;
    }

    public TileMatrix getTileMatrix() {
        return tileMatrix;
    }

    public TileIterator getTileIterator() {
        return getTileIterator(options.getTileMatrixOrigin());
    }

    public TileIterator getTileIterator(TileMatrixOrigin origin) {
        return tileMatrix.getTileIterator(origin);
    }

    public Tile getTileAt(int column, int row) {
        return getTileAt(column, row, options.getTileMatrixOrigin());
    }

    public Tile getTileAt(int column, int row, TileMatrixOrigin origin) {
        return tileMatrix.getTileAt(column, row, origin);
    }
}
