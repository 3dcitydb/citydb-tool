/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

package org.citydb.operation.importer.property;

import org.citydb.database.schema.Sequence;
import org.citydb.model.property.GeometryProperty;
import org.citydb.model.property.PropertyDescriptor;
import org.citydb.operation.importer.ImportException;
import org.citydb.operation.importer.ImportHelper;
import org.citydb.operation.importer.geometry.GeometryImporter;

import java.sql.SQLException;
import java.util.Collections;

public class GeometryPropertyImporter extends PropertyImporter {

    public GeometryPropertyImporter(ImportHelper helper) throws SQLException {
        super(helper);
    }

    @Override
    protected String getInsertStatement() {
        return "insert into " + tableHelper.getPrefixedTableName(table) +
                "(id, feature_id, parent_id, datatype_id, namespace_id, name, " +
                "val_lod, val_geometry_id) " +
                "values (" + String.join(",", Collections.nCopies(8, "?")) + ")";
    }

    public PropertyDescriptor doImport(GeometryProperty property, long featureId) throws ImportException, SQLException {
        long propertyId = nextSequenceValue(Sequence.PROPERTY);
        return doImport(property, propertyId, propertyId, featureId);
    }

    PropertyDescriptor doImport(GeometryProperty property, long parentId, long featureId) throws ImportException, SQLException {
        return doImport(property, nextSequenceValue(Sequence.PROPERTY), parentId, featureId);
    }

    PropertyDescriptor doImport(GeometryProperty property, long propertyId, long parentId, long featureId) throws ImportException, SQLException {
        stmt.setString(7, property.getLod().orElse(null));
        stmt.setLong(8, tableHelper.getOrCreateImporter(GeometryImporter.class)
                .doImport(property.getObject(), false, featureId)
                .getId());

        return super.doImport(property, propertyId, parentId, featureId);
    }
}
