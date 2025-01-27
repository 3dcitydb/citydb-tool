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

import com.alibaba.fastjson2.annotation.JSONField;
import com.alibaba.fastjson2.annotation.JSONType;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.geometry.GeometryException;
import org.citydb.database.srs.SpatialReference;
import org.citydb.database.srs.SrsException;
import org.citydb.database.util.SrsHelper;
import org.citydb.model.encoding.PointReader;
import org.citydb.model.encoding.PointWriter;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.geometry.Point;
import org.citydb.util.tiling.TileMatrix;
import org.citydb.util.tiling.TilingException;
import org.citydb.util.tiling.TilingScheme;

import java.sql.SQLException;
import java.util.Optional;

@JSONType(typeName = "Dimension")
public class DimensionScheme extends TilingScheme {
    private Dimension width;
    private Dimension height;
    @JSONField(serializeUsing = PointWriter.class, deserializeUsing = PointReader.class)
    private Point gridPoint;

    public static DimensionScheme of(double width, double height) {
        return of(Dimension.of(width), Dimension.of(height));
    }

    public static DimensionScheme of(Dimension width, Dimension height) {
        return of(width, height, null);
    }

    public static DimensionScheme of(Dimension width, Dimension height, Point gridPoint) {
        return new DimensionScheme()
                .setWidth(width)
                .setHeight(height)
                .setGridPoint(gridPoint);
    }

    public Optional<Dimension> getHeight() {
        return Optional.ofNullable(height);
    }

    public DimensionScheme setHeight(Dimension height) {
        this.height = height;
        return this;
    }

    public Optional<Dimension> getWidth() {
        return Optional.ofNullable(width);
    }

    public DimensionScheme setWidth(Dimension width) {
        this.width = width;
        return this;
    }

    public Optional<Point> getGridPoint() {
        return Optional.ofNullable(gridPoint);
    }

    public DimensionScheme setGridPoint(Point gridPoint) {
        this.gridPoint = gridPoint;
        return this;
    }

    @Override
    protected TileMatrix buildTileMatrix(Envelope extent, DatabaseAdapter adapter) throws TilingException {
        if (width == null) {
            throw new TilingException("No tile width provided for the dimension tiling scheme.");
        } else if (width.getValue() <= 0) {
            throw new TilingException("The tile width must be a positive number but was " + width.getValue() + ".");
        } else if (height == null) {
            throw new TilingException("No tile height provided for the dimension tiling scheme.");
        } else if (height.getValue() <= 0) {
            throw new TilingException("The tile height must be a positive number but was " + height.getValue() + ".");
        }

        double tileWidth, tileHeight;
        try {
            SrsHelper helper = adapter.getGeometryAdapter().getSrsHelper();
            tileWidth = helper.convert(width.getValue(), width.getUnit().orElse(null));
            tileHeight = helper.convert(height.getValue(), height.getUnit().orElse(null));
        } catch (SrsException e) {
            throw new TilingException("Failed to convert tile dimension to the unit of the database SRS.", e);
        }

        double gridX, gridY;
        if (gridPoint != null) {
            try {
                SpatialReference reference = adapter.getGeometryAdapter().getSpatialReference(gridPoint)
                        .orElse(adapter.getDatabaseMetadata().getSpatialReference());
                Point point = reference.getSRID() != adapter.getDatabaseMetadata().getSpatialReference().getSRID() ?
                        adapter.getGeometryAdapter().transform(gridPoint) :
                        gridPoint;
                gridX = point.getCoordinate().getX();
                gridY = point.getCoordinate().getY();
            } catch (GeometryException | SrsException | SQLException e) {
                throw new TilingException("Failed to transform the grid point to the database SRS.", e);
            }
        } else {
            gridX = gridY = 0;
        }

        double minX = getNearestNeighbor(gridX, extent.getLowerCorner().getX(), tileWidth);
        double minY = getNearestNeighbor(gridY, extent.getLowerCorner().getY(), tileHeight);
        int columns = (int) Math.ceil((extent.getUpperCorner().getX() - minX) / tileWidth);
        int rows = (int) Math.ceil((extent.getUpperCorner().getY() - minY) / tileHeight);
        Coordinate lowerCorner = Coordinate.of(minX, minY);
        Coordinate upperCorner = Coordinate.of(minX + columns * tileWidth, minY + rows * tileHeight);

        return buildTileMatrix(lowerCorner, upperCorner, columns, rows, tileWidth, tileHeight, adapter);
    }

    private double getNearestNeighbor(double gridValue, double candidate, double offset) {
        return gridValue >= candidate ?
                gridValue - Math.ceil((gridValue - candidate) / offset) * offset :
                gridValue + Math.floor((candidate - gridValue) / offset) * offset;
    }
}
