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
import org.citydb.model.appearance.*;
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

        SequenceValues values = new SequenceValues(processor.implicitGeometries);
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
        private Set<String> implicitGeometries;
        private SQLException exception;

        @Override
        public void visit(Feature feature) {
            counter.merge(Sequence.FEATURE, 1, Integer::sum);
            super.visit(feature);
        }

        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
            try {
                if (!lookupAndPut(implicitGeometry)) {
                    counter.merge(Sequence.IMPLICIT_GEOMETRY, 1, Integer::sum);
                    implicitGeometry.getGeometry().ifPresent(geometry ->
                            counter.merge(Sequence.GEOMETRY_DATA, 1, Integer::sum));
                    cache(implicitGeometry);
                    super.visit(implicitGeometry);
                }
            } catch (SQLException e) {
                setShouldWalk(false);
                exception = e;
            }
        }

        @Override
        public void visit(Appearance appearance) {
            counter.merge(Sequence.APPEARANCE, 1, Integer::sum);
            super.visit(appearance);
        }

        @Override
        public void visit(Address address) {
            counter.merge(Sequence.ADDRESS, 1, Integer::sum);
            super.visit(address);
        }

        @Override
        public void visit(SurfaceData<?> surfaceData) {
            counter.merge(Sequence.SURFACE_DATA, 1, Integer::sum);
            super.visit(surfaceData);
        }

        @Override
        public void visit(Texture<?> texture) {
            if (texture.getTextureImageProperty()
                    .map(TextureImageProperty::getObject)
                    .isPresent()) {
                counter.merge(Sequence.TEX_IMAGE, 1, Integer::sum);
            }

            super.visit(texture);
        }

        @Override
        public void visit(Property<?> property) {
            counter.merge(Sequence.PROPERTY, 1, Integer::sum);
            super.visit(property);
        }

        @Override
        public void visit(SurfaceDataProperty property) {
            counter.merge(Sequence.APPEAR_TO_SURFACE_DATA, 1, Integer::sum);
            super.visit(property);
        }

        @Override
        public void visit(GeometryProperty property) {
            counter.merge(Sequence.GEOMETRY_DATA, 1, Integer::sum);
            super.visit(property);
        }

        private void cache(ImplicitGeometry implicitGeometry) {
            if (implicitGeometries == null) {
                implicitGeometries = new HashSet<>();
            }

            implicitGeometries.add(implicitGeometry.getObjectId().orElse(null));
        }
    }

    private boolean lookupAndPut(ImplicitGeometry implicitGeometry) throws SQLException {
        String objectId = implicitGeometry.getObjectId().orElse(null);
        return objectId != null
                && (helper.getOrCreatePersistentMap("implicit-geometries").putIfAbsent(objectId, true) != null
                || lookupImplicitGeometry(objectId));
    }

    private boolean lookupImplicitGeometry(String objectId) throws SQLException {
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

        return false;
    }
}
