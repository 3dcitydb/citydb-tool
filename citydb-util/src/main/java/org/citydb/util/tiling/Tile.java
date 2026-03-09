/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
                SpatialReference reference = adapter.getGeometryAdapter().getSrsHelper().getSpatialReference(extent)
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
