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
import com.alibaba.fastjson2.annotation.JSONField;
import org.citydb.config.SerializableConfig;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.geometry.GeometryException;
import org.citydb.database.srs.SpatialReference;
import org.citydb.database.srs.SrsException;
import org.citydb.model.encoding.EnvelopeReader;
import org.citydb.model.encoding.EnvelopeWriter;
import org.citydb.model.geometry.Envelope;
import org.citydb.util.tiling.options.TileMatrixOrigin;

import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

@SerializableConfig(name = "tilingOptions")
public class Tiling {
    @JSONField(serializeUsing = EnvelopeWriter.class, deserializeUsing = EnvelopeReader.class)
    private Envelope extent;
    private TilingScheme scheme;
    @JSONField(serializeFeatures = JSONWriter.Feature.WriteEnumUsingToString)
    private TileMatrixOrigin tileMatrixOrigin = TileMatrixOrigin.TOP_LEFT;

    public static Tiling newInstance() {
        return new Tiling();
    }

    public static Tiling of(Envelope extent, TilingScheme scheme) {
        return new Tiling().setExtent(extent).setScheme(scheme);
    }

    public Optional<Envelope> getExtent() {
        return Optional.ofNullable(extent);
    }

    public Tiling setExtent(Envelope extent) {
        this.extent = extent;
        return this;
    }

    public Optional<TilingScheme> getScheme() {
        return Optional.ofNullable(scheme);
    }

    public Tiling setScheme(TilingScheme scheme) {
        this.scheme = scheme;
        return this;
    }

    public TileMatrixOrigin getTileMatrixOrigin() {
        return tileMatrixOrigin != null ? tileMatrixOrigin : TileMatrixOrigin.TOP_LEFT;
    }

    public Tiling setTileMatrixOrigin(TileMatrixOrigin tileMatrixOrigin) {
        this.tileMatrixOrigin = tileMatrixOrigin;
        return this;
    }

    public TileMatrix buildTileMatrix(DatabaseAdapter adapter) throws TilingException {
        Objects.requireNonNull(adapter, "The database adapter must not be null.");

        if (extent == null) {
            throw new TilingException("No tiling extent specified.");
        } else if (scheme == null) {
            throw new TilingException("No tiling scheme specified.");
        }

        Envelope extent;
        try {
            SpatialReference reference = adapter.getGeometryAdapter().getSpatialReference(this.extent)
                    .orElse(adapter.getDatabaseMetadata().getSpatialReference());
            extent = reference.getSRID() != adapter.getDatabaseMetadata().getSpatialReference().getSRID() ?
                    adapter.getGeometryAdapter().transform(this.extent) :
                    this.extent;
        } catch (GeometryException | SrsException | SQLException e) {
            throw new TilingException("Failed to transform the tiling extent to the database SRS.", e);
        }

        return scheme.buildTileMatrix(extent, adapter)
                .setTileMatrixOrigin(getTileMatrixOrigin());
    }
}
