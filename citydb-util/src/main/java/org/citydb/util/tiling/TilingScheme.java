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
