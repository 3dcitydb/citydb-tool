/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
