/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.importer.property;

import com.alibaba.fastjson2.JSONArray;
import org.citydb.database.schema.Sequence;
import org.citydb.model.property.*;
import org.citydb.operation.importer.ImportException;
import org.citydb.operation.importer.ImportHelper;

import java.sql.SQLException;
import java.util.Collections;

public class AttributeImporter extends PropertyImporter {

    public AttributeImporter(ImportHelper helper) throws SQLException {
        super(helper);
    }

    @Override
    protected String getInsertStatement() {
        return "insert into " + tableHelper.getPrefixedTableName(table) +
                "(id, feature_id, parent_id, datatype_id, namespace_id, name, " +
                "val_int, val_double, val_string, val_timestamp, val_uri, val_codespace, val_uom, val_array, " +
                "val_content, val_content_mime_type) " +
                "values (" + String.join(",", Collections.nCopies(16, "?")) + ")";
    }

    public PropertyDescriptor doImport(Attribute attribute, long featureId) throws ImportException, SQLException {
        long propertyId = nextSequenceValue(Sequence.PROPERTY);
        return doImport(attribute, propertyId, propertyId, featureId);
    }

    PropertyDescriptor doImport(Attribute attribute, long parentId, long featureId) throws ImportException, SQLException {
        return doImport(attribute, nextSequenceValue(Sequence.PROPERTY), parentId, featureId);
    }

    private PropertyDescriptor doImport(Attribute attribute, long propertyId, long parentId, long featureId) throws ImportException, SQLException {
        setLongOrNull(7, attribute.getIntValue().orElse(null));
        setDoubleOrNull(8, attribute.getDoubleValue().orElse(null));
        setStringOrNull(9, attribute.getStringValue().orElse(null));
        setTimestampOrNull(10, attribute.getTimeStamp().orElse(null));
        setStringOrNull(11, attribute.getURI().orElse(null));
        setStringOrNull(12, attribute.getCodeSpace().orElse(null));
        setStringOrNull(13, attribute.getUom().orElse(null));

        JSONArray arrayValue = attribute.getArrayValue()
                .map(array -> new JSONArray(array.getValues().stream()
                        .map(Value::rawValue)
                        .toList()))
                .orElse(null);

        setJsonOrNull(14, getJson(arrayValue));
        setStringOrNull(15, attribute.getGenericContent().orElse(null));
        setStringOrNull(16, attribute.getGenericContentMimeType().orElse(null));

        PropertyDescriptor descriptor = super.doImport(attribute, propertyId, parentId, featureId);

        if (attribute.hasProperties()) {
            for (Property<?> child : attribute.getProperties().getAll()) {
                if (child instanceof Attribute childAttribute) {
                    doImport(childAttribute, parentId, featureId);
                } else if (child instanceof FeatureProperty property) {
                    tableHelper.getOrCreateImporter(FeaturePropertyImporter.class)
                            .doImport(property, parentId, featureId);
                } else if (child instanceof GeometryProperty property) {
                    tableHelper.getOrCreateImporter(GeometryPropertyImporter.class)
                            .doImport(property, parentId, featureId);
                } else if (child instanceof ImplicitGeometryProperty property) {
                    tableHelper.getOrCreateImporter(ImplicitGeometryPropertyImporter.class)
                            .doImport(property, parentId, featureId);
                } else if (child instanceof AppearanceProperty property) {
                    tableHelper.getOrCreateImporter(AppearancePropertyImporter.class)
                            .doImport(property, parentId, featureId);
                } else if (child instanceof AddressProperty property) {
                    tableHelper.getOrCreateImporter(AddressPropertyImporter.class)
                            .doImport(property, parentId, featureId);
                }
            }
        }

        return descriptor;
    }
}
