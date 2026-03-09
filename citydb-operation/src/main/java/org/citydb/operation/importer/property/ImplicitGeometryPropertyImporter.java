/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.importer.property;

import com.alibaba.fastjson2.JSONArray;
import org.citydb.database.schema.Sequence;
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
                "val_lod, val_implicitgeom_id, val_implicitgeom_refpoint, val_array) " +
                "values (" + String.join(",", Collections.nCopies(10, "?")) + ")";
    }

    public PropertyDescriptor doImport(ImplicitGeometryProperty property, long featureId) throws ImportException, SQLException {
        long propertyId = nextSequenceValue(Sequence.PROPERTY);
        return doImport(property, propertyId, propertyId, featureId);
    }

    PropertyDescriptor doImport(ImplicitGeometryProperty property, long parentId, long featureId) throws ImportException, SQLException {
        return doImport(property, nextSequenceValue(Sequence.PROPERTY), parentId, featureId);
    }

    PropertyDescriptor doImport(ImplicitGeometryProperty property, long propertyId, long parentId, long featureId) throws ImportException, SQLException {
        setStringOrNull(7, property.getLod().orElse(null));

        ImplicitGeometry implicitGeometry = property.getObject().orElse(null);
        if (implicitGeometry != null && canImport(implicitGeometry)) {
            stmt.setLong(8, tableHelper.getOrCreateImporter(ImplicitGeometryImporter.class)
                    .doImport(implicitGeometry, featureId));
        } else {
            String reference = implicitGeometry != null ?
                    implicitGeometry.getOrCreateObjectId() :
                    property.getReference().orElseThrow(() -> new ImportException("The implicit geometry property " +
                            "contains neither an object nor a reference."));
            cacheReference(CacheType.IMPLICIT_GEOMETRY, reference, propertyId);
            stmt.setNull(8, Types.BIGINT);
        }

        setGeometryOrNull(9, getGeometry(property.getReferencePoint().orElse(null), true));

        JSONArray transformationMatrix = property.getTransformationMatrix()
                .map(Matrix::toRowMajor)
                .map(JSONArray::new)
                .orElse(null);

        setJsonOrNull(10, getJson(transformationMatrix));

        return super.doImport(property, propertyId, parentId, featureId);
    }
}
