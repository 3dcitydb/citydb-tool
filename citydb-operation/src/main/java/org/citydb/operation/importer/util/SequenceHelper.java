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

package org.citydb.operation.importer.util;

import org.citydb.database.schema.Sequence;
import org.citydb.database.schema.Table;
import org.citydb.model.address.Address;
import org.citydb.model.appearance.Appearance;
import org.citydb.model.appearance.SurfaceData;
import org.citydb.model.appearance.SurfaceDataProperty;
import org.citydb.model.appearance.Texture;
import org.citydb.model.common.ExternalFile;
import org.citydb.model.common.Referencable;
import org.citydb.model.common.Visitable;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.property.GeometryProperty;
import org.citydb.model.property.Property;
import org.citydb.model.walker.ModelWalker;
import org.citydb.operation.importer.ImportHelper;
import org.citydb.operation.importer.reference.CacheType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SequenceHelper {
    private final ImportHelper helper;
    private final Map<String, PreparedStatement> statements = new HashMap<>();

    public SequenceHelper(ImportHelper helper) {
        this.helper = helper;
    }

    public SequenceValues nextSequenceValues(Visitable visitable) throws SQLException {
        Processor processor = new Processor();
        visitable.accept(processor);
        if (processor.exception != null) {
            throw processor.exception;
        }

        SequenceValues values = new SequenceValues(processor.idCache);
        for (Map.Entry<Sequence, Integer> entry : processor.counter.entrySet()) {
            Sequence sequence = entry.getKey();
            PreparedStatement statement = getOrCreateStatement(sequence.getName(),
                    helper.getAdapter().getSchemaAdapter().getNextSequenceValues(sequence));
            statement.setInt(1, entry.getValue());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    values.addValue(sequence, rs.getLong(1));
                }
            }
        }

        return values;
    }

    private PreparedStatement getOrCreateStatement(String name, String sql) throws SQLException {
        PreparedStatement statement = statements.get(name);
        if (statement == null) {
            statement = helper.getConnection().prepareStatement(sql);
            statements.put(name, statement);
        }

        return statement;
    }

    public void close() {
        for (PreparedStatement stmt : statements.values()) {
            try {
                stmt.close();
            } catch (SQLException e) {
                //
            }
        }
    }

    private class Processor extends ModelWalker {
        private final Map<Sequence, Integer> counter = new HashMap<>();
        private final Map<CacheType, Set<String>> idCache = new HashMap<>();
        private SQLException exception;

        @Override
        public void visit(Feature feature) {
            count(Sequence.FEATURE);
            super.visit(feature);
        }

        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
            try {
                if (!lookup(CacheType.IMPLICIT_GEOMETRY, implicitGeometry)
                        && !helper.lookupAndPut(implicitGeometry)
                        && !existsInDatabase(implicitGeometry)) {
                    count(Sequence.IMPLICIT_GEOMETRY);
                    implicitGeometry.getGeometry().ifPresent(geometry -> count(Sequence.GEOMETRY_DATA));
                    cache(CacheType.IMPLICIT_GEOMETRY, implicitGeometry);
                    super.visit(implicitGeometry);
                }
            } catch (SQLException e) {
                setShouldWalk(false);
                exception = e;
            }
        }

        @Override
        public void visit(Appearance appearance) {
            count(Sequence.APPEARANCE);
            super.visit(appearance);
        }

        @Override
        public void visit(Address address) {
            count(Sequence.ADDRESS);
            super.visit(address);
        }

        @Override
        public void visit(SurfaceData<?> surfaceData) {
            count(Sequence.SURFACE_DATA);
            super.visit(surfaceData);
        }

        @Override
        public void visit(Texture<?> texture) {
            ExternalFile textureImage = texture.getTextureImage().orElse(null);
            if (textureImage != null
                    && !lookup(CacheType.TEXTURE_IMAGE, textureImage)
                    && !helper.lookupAndPut(textureImage)) {
                count(Sequence.TEX_IMAGE);
                cache(CacheType.TEXTURE_IMAGE, textureImage);
            }

            super.visit(texture);
        }

        @Override
        public void visit(Property<?> property) {
            count(Sequence.PROPERTY);
            super.visit(property);
        }

        @Override
        public void visit(SurfaceDataProperty property) {
            count(Sequence.APPEAR_TO_SURFACE_DATA);
            super.visit(property);
        }

        @Override
        public void visit(GeometryProperty property) {
            count(Sequence.GEOMETRY_DATA);
            super.visit(property);
        }

        private void count(Sequence sequence) {
            counter.merge(sequence, 1, Integer::sum);
        }

        private boolean lookup(CacheType type, Referencable object) {
            Set<String> ids = idCache.get(type);
            if (ids != null) {
                String objectId = object.getObjectId().orElse(null);
                return objectId != null && ids.contains(objectId);
            } else {
                return false;
            }
        }

        private void cache(CacheType type, Referencable object) {
            idCache.computeIfAbsent(type, k -> new HashSet<>()).add(object.getObjectId().orElse(null));
        }
    }

    private boolean existsInDatabase(ImplicitGeometry implicitGeometry) throws SQLException {
        String objectId = implicitGeometry.getObjectId().orElse(null);
        if (objectId != null) {
            PreparedStatement statement = getOrCreateStatement("lookup-implicit-geometry",
                    "select id from " + helper.getTableHelper().getPrefixedTableName(Table.IMPLICIT_GEOMETRY) +
                            " where objectid = ? fetch first 1 rows only");

            statement.setString(1, objectId);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    helper.getOrCreateReferenceCache(CacheType.IMPLICIT_GEOMETRY).putTarget(objectId, rs.getLong(1));
                    return true;
                }
            }
        }

        return false;
    }
}
