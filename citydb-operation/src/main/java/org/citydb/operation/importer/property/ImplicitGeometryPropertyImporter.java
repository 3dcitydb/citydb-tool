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

package org.citydb.operation.importer.property;

import com.alibaba.fastjson2.JSONArray;
import org.citydb.database.schema.Sequence;
import org.citydb.model.common.Reference;
import org.citydb.model.common.RelationType;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.property.ImplicitGeometryProperty;
import org.citydb.model.property.PropertyDescriptor;
import org.citydb.model.util.matrix.Matrix;
import org.citydb.operation.importer.ImportException;
import org.citydb.operation.importer.ImportHelper;
import org.citydb.operation.importer.geometry.ImplicitGeometryImporter;
import org.citydb.operation.importer.reference.CacheType;

import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;

public class ImplicitGeometryPropertyImporter extends PropertyImporter {

    public ImplicitGeometryPropertyImporter(ImportHelper helper) throws SQLException {
        super(helper);
    }

    @Override
    protected String getInsertStatement() {
        return "insert into " + tableHelper.getPrefixedTableName(table) +
                "(id, feature_id, parent_id, datatype_id, namespace_id, name, " +
                "val_lod, val_implicitgeom_id, val_implicitgeom_refpoint, val_array, " +
                "val_relation_type) " +
                "values (" + String.join(",", Collections.nCopies(10, "?")) + ", " +
                RelationType.CONTAINS.getDatabaseValue() + ")";
    }

    public PropertyDescriptor doImport(ImplicitGeometryProperty property, long featureId) throws ImportException, SQLException {
        long propertyId = nextSequenceValue(Sequence.PROPERTY);
        return doImport(property, propertyId, propertyId, featureId);
    }

    PropertyDescriptor doImport(ImplicitGeometryProperty property, long parentId, long featureId) throws ImportException, SQLException {
        return doImport(property, nextSequenceValue(Sequence.PROPERTY), parentId, featureId);
    }

    PropertyDescriptor doImport(ImplicitGeometryProperty property, long propertyId, long parentId, long featureId) throws ImportException, SQLException {
        stmt.setString(7, property.getLod().orElse(null));

        ImplicitGeometry implicitGeometry = property.getObject().orElse(null);
        if (implicitGeometry != null) {
            stmt.setLong(8, tableHelper.getOrCreateImporter(ImplicitGeometryImporter.class)
                    .doImport(implicitGeometry, featureId));
        } else if (property.getReference().isPresent()) {
            Reference reference = property.getReference().get();
            cacheReference(CacheType.IMPLICIT_GEOMETRY, reference, propertyId);
            stmt.setNull(8, Types.BIGINT);
        }

        Object referencePoint = getGeometry(property.getReferencePoint().orElse(null), true);
        if (referencePoint != null) {
            stmt.setObject(9, referencePoint, adapter.getGeometryAdapter().getGeometrySqlType());
        } else {
            stmt.setNull(9, adapter.getGeometryAdapter().getGeometrySqlType(),
                    adapter.getGeometryAdapter().getGeometryTypeName());
        }

        String transformationMatrix = property.getTransformationMatrix()
                .map(Matrix::toRowMajor)
                .map(JSONArray::new)
                .map(JSONArray::toString)
                .orElse(null);
        if (transformationMatrix != null) {
            stmt.setObject(10, transformationMatrix, Types.OTHER);
        } else {
            stmt.setNull(10, Types.OTHER);
        }

        return super.doImport(property, propertyId, parentId, featureId);
    }
}
