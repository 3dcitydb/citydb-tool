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

package org.citydb.operation.importer.appearance;

import org.citydb.database.schema.Sequence;
import org.citydb.database.schema.Table;
import org.citydb.model.appearance.Appearance;
import org.citydb.model.appearance.AppearanceDescriptor;
import org.citydb.model.appearance.SurfaceDataProperty;
import org.citydb.operation.importer.ImportException;
import org.citydb.operation.importer.ImportHelper;
import org.citydb.operation.importer.common.DatabaseImporter;

import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;

public class AppearanceImporter extends DatabaseImporter {
    public enum Type {FEATURE, IMPLICIT_GEOMETRY, GLOBAL}

    public AppearanceImporter(ImportHelper helper) throws SQLException {
        super(Table.APPEARANCE, helper);
    }

    @Override
    protected String getInsertStatement() {
        return "insert into " + tableHelper.getPrefixedTableName(table) +
                "(id, objectid, identifier, identifier_codespace, theme, is_global, feature_id, " +
                "implicit_geometry_id) " +
                "values (" + String.join(",", Collections.nCopies(8, "?")) + ")";
    }

    public AppearanceDescriptor doImport(Appearance appearance, long targetId, Type type) throws ImportException, SQLException {
        long appearanceId = nextSequenceValue(Sequence.APPEARANCE);

        stmt.setLong(1, appearanceId);
        stmt.setString(2, appearance.getOrCreateObjectId());
        setStringOrNull(3, appearance.getIdentifier().orElse(null));
        setStringOrNull(4, appearance.getIdentifierCodeSpace().orElse(null));
        setStringOrNull(5, appearance.getTheme().orElse(null));

        switch (type) {
            case GLOBAL:
                stmt.setInt(6, 1);
                stmt.setNull(7, Types.BIGINT);
                stmt.setNull(8, Types.BIGINT);
                break;
            case IMPLICIT_GEOMETRY:
                stmt.setInt(6, 0);
                stmt.setNull(7, Types.BIGINT);
                stmt.setLong(8, targetId);
                break;
            default:
                stmt.setInt(6, 0);
                stmt.setLong(7, targetId);
                stmt.setNull(8, Types.BIGINT);
                break;
        }

        addBatch();

        if (appearance.hasSurfaceData()) {
            for (SurfaceDataProperty property : appearance.getSurfaceData()) {
                tableHelper.getOrCreateImporter(SurfaceDataPropertyImporter.class).doImport(property, appearanceId);
            }
        }

        AppearanceDescriptor descriptor = AppearanceDescriptor.of(appearanceId);
        if (type == Type.FEATURE) {
            descriptor.setFeatureId(targetId);
        } else if (type == Type.IMPLICIT_GEOMETRY) {
            descriptor.setImplicitGeometryId(targetId);
        }

        appearance.setDescriptor(descriptor);
        return descriptor;
    }
}
