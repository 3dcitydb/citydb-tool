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

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.geometry.GeometryException;
import org.citydb.database.srs.SpatialReference;
import org.citydb.database.srs.SrsException;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.geometry.Point;

import java.sql.SQLException;

public class Tile {
    private final Envelope extent;
    private final int column;
    private final int row;
    private final DatabaseAdapter adapter;

    Tile(Envelope extent, int column, int row, DatabaseAdapter adapter) {
        this.extent = extent;
        this.column = column;
        this.row = row;
        this.adapter = adapter;
    }

    public Envelope getExtent() {
        return extent;
    }

    public int getColumn() {
        return column;
    }

    public int getRow() {
        return row;
    }

    public boolean isOnTile(Envelope extent) throws TilingException {
        return extent != null && isOnTile(Point.of(extent.getCenter())
                .setSRID(extent.getSRID().orElse(null))
                .setSrsIdentifier(extent.getSrsIdentifier().orElse(null)));
    }

    public boolean isOnTile(Point point) throws TilingException {
        if (point != null) {
            try {
                SpatialReference reference = adapter.getGeometryAdapter().getSpatialReference(extent)
                        .orElse(adapter.getDatabaseMetadata().getSpatialReference());
                if (reference.getSRID() != adapter.getDatabaseMetadata().getSpatialReference().getSRID()) {
                    point = adapter.getGeometryAdapter().transform(point);
                }
            } catch (GeometryException | SrsException | SQLException e) {
                throw new TilingException("Failed to transform the point geometry to the database SRS.", e);
            }

            return extent.isOnTile(point.getCoordinate());
        } else {
            return false;
        }
    }
}
