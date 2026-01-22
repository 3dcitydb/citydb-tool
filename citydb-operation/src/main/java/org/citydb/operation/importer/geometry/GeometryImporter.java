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

package org.citydb.operation.importer.geometry;

import com.alibaba.fastjson2.JSONWriter;
import org.citydb.database.schema.Sequence;
import org.citydb.database.schema.Table;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.geometry.GeometryDescriptor;
import org.citydb.operation.importer.ImportException;
import org.citydb.operation.importer.ImportHelper;
import org.citydb.operation.importer.common.DatabaseImporter;

import java.sql.SQLException;
import java.util.Collections;

public class GeometryImporter extends DatabaseImporter {

    public GeometryImporter(ImportHelper helper) throws SQLException {
        super(Table.GEOMETRY_DATA, helper);
    }

    @Override
    protected String getInsertStatement() {
        return "insert into " + tableHelper.getPrefixedTableName(table) +
                "(id, geometry, implicit_geometry, geometry_properties, feature_id) " +
                "values (" + String.join(",", Collections.nCopies(5, "?")) + ")";
    }

    public GeometryDescriptor doImport(Geometry<?> geometry, boolean isImplicit, long featureId) throws ImportException, SQLException {
        long geometryId = nextSequenceValue(Sequence.GEOMETRY_DATA);
        stmt.setLong(1, geometryId);

        Object value;
        int geometryIndex, nullIndex;
        if (isImplicit) {
            value = getImplicitGeometry(geometry);
            geometryIndex = 3;
            nullIndex = 2;
        } else {
            value = getGeometry(geometry, true);
            geometryIndex = 2;
            nullIndex = 3;
        }

        setGeometryOrNull(geometryIndex, value);
        setGeometryOrNull(nullIndex, null);
        setJsonOrNull(4, getJson(adapter.getGeometryAdapter().buildGeometryProperties(geometry),
                JSONWriter.Feature.LargeObject));
        stmt.setLong(5, featureId);

        addBatch();

        GeometryDescriptor descriptor = GeometryDescriptor.of(geometryId, featureId);
        geometry.setDescriptor(descriptor);
        return descriptor;
    }
}
