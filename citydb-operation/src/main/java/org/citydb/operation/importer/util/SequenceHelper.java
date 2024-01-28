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

package org.citydb.operation.importer.util;

import org.citydb.database.schema.Sequence;
import org.citydb.model.address.Address;
import org.citydb.model.appearance.*;
import org.citydb.model.common.Visitable;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.property.GeometryProperty;
import org.citydb.model.property.Property;
import org.citydb.model.walker.ModelWalker;
import org.citydb.operation.importer.ImportHelper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class SequenceHelper {
    private final ImportHelper helper;
    private final Map<Sequence, PreparedStatement> statements = new EnumMap<>(Sequence.class);

    public SequenceHelper(ImportHelper helper) {
        this.helper = helper;
    }

    public SequenceValues nextSequenceValues(Visitable visitable) throws SQLException {
        SequenceValues values = new SequenceValues();

        Map<Sequence, Integer> counter = new HashMap<>();
        visitable.accept(new ObjectCounter(counter));

        for (Map.Entry<Sequence, Integer> entry : counter.entrySet()) {
            PreparedStatement stmt = getOrCreateStatement(entry.getKey());
            stmt.setInt(1, entry.getValue());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    values.addValue(entry.getKey(), rs.getLong(1));
                }
            }
        }

        return values;
    }

    private PreparedStatement getOrCreateStatement(Sequence sequence) throws SQLException {
        PreparedStatement stmt = statements.get(sequence);
        if (stmt == null) {
            stmt = helper.getConnection().prepareStatement(
                    helper.getAdapter().getSchemaAdapter().getNextSequenceValues(sequence));
            statements.put(sequence, stmt);
        }

        return stmt;
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

    private static class ObjectCounter extends ModelWalker {
        final Map<Sequence, Integer> counter;

        ObjectCounter(Map<Sequence, Integer> counter) {
            this.counter = counter;
        }

        @Override
        public void visit(Feature feature) {
            counter.merge(Sequence.FEATURE, 1, Integer::sum);
            super.visit(feature);
        }

        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
            counter.merge(Sequence.IMPLICIT_GEOMETRY, 1, Integer::sum);
            implicitGeometry.getGeometry().ifPresent(geometry ->
                    counter.merge(Sequence.GEOMETRY_DATA, 1, Integer::sum));
            super.visit(implicitGeometry);
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
    }
}
