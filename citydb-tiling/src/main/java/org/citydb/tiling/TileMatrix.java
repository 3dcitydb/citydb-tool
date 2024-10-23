/*
 * 3D City Database - The Open Source CityGML Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2013 - 2024
 * Chair of Geoinformatics
 * Technical University of Munich, Germany
 * https://www.lrg.tum.de/gis/
 *
 * The 3D City Database is jointly developed with the following
 * cooperation partners:
 *
 * Virtual City Systems, Berlin <https://vc.systems/>
 * M.O.S.S. Computer Grafik Systeme GmbH, Taufkirchen <http://www.moss.de/>
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
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Envelope;
import org.citydb.tiling.options.TileMatrixOrigin;
import org.geotools.referencing.CRS;

public class TileMatrix {
    private final Envelope extent;
    private final int columns;
    private final int rows;
    private final double tileWidth;
    private final double tileHeight;
    private final DatabaseAdapter adapter;
    private final double[] columnOffsets;
    private final double[] rowOffsets;
    private final boolean swapAxes;

    TileMatrix(Coordinate lowerCorner, Coordinate upperCorner, int columns, int rows, double tileWidth,
               double tileHeight, DatabaseAdapter adapter) throws TilingException {
        this.columns = columns > 0 ? columns : 1;
        this.rows = rows > 0 ? rows : 1;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.adapter = adapter;

        columnOffsets = computeOffsets(columns, tileWidth);
        rowOffsets = computeOffsets(rows, tileHeight);
        extent = Envelope.of(lowerCorner, upperCorner)
                .setSRID(adapter.getDatabaseMetadata().getSpatialReference().getSRID());
        swapAxes = CRS.getAxisOrder(adapter.getDatabaseMetadata().getSpatialReference().getDefinition()
                        .orElseThrow(() -> new TilingException("Failed to retrieve the database SRS definition.")))
                .equals(CRS.AxisOrder.NORTH_EAST);
    }

    public Envelope getExtent() {
        return extent;
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    public double getTileWidth() {
        return tileWidth;
    }

    public double getTileHeight() {
        return tileHeight;
    }

    TileIterator getTileIterator(TileMatrixOrigin origin) {
        return new TileIterator(this, origin);
    }

    Tile getTileAt(int column, int row, TileMatrixOrigin origin) {
        if (column < 0 || column >= columns || row < 0 || row >= rows) {
            throw new IndexOutOfBoundsException("Tile index (" + column + "," + row + ") is out of bounds.");
        }

        double minX, minY, maxX, maxY;
        if (swapAxes) {
            minX = extent.getUpperCorner().getY();
            minY = extent.getUpperCorner().getX();
            maxX = extent.getLowerCorner().getY();
            maxY = extent.getLowerCorner().getX();
        } else {
            minX = extent.getLowerCorner().getX();
            minY = extent.getLowerCorner().getY();
            maxX = extent.getUpperCorner().getX();
            maxY = extent.getUpperCorner().getY();
        }

        int rowIndex = origin == TileMatrixOrigin.BOTTOM_LEFT ? rows - row - 1 : row;
        Coordinate lowerCorner = Coordinate.of(
                minX + columnOffsets[column],
                rowIndex == rows - 1 ? minY : maxY - rowOffsets[rowIndex + 1]);
        Coordinate upperCorner = Coordinate.of(
                column == columns - 1 ? maxX : minX + columnOffsets[column + 1],
                maxY - rowOffsets[rowIndex]);

        return new Tile(Envelope.of(lowerCorner, upperCorner)
                .setSRID(adapter.getDatabaseMetadata().getSpatialReference().getSRID()),
                column, row, adapter);
    }

    private double[] computeOffsets(int size, double offset) {
        double[] offsets = new double[size];
        offsets[0] = 0;
        for (int i = 1; i < offsets.length; i++) {
            offsets[i] = offsets[i - 1] + offset;
        }

        return offsets;
    }
}
