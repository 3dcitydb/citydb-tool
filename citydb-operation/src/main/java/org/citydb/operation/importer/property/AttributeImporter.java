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

package org.citydb.operation.importer.property;

import com.alibaba.fastjson2.JSONArray;
import org.citydb.database.schema.Sequence;
import org.citydb.model.property.*;
import org.citydb.operation.importer.ImportException;
import org.citydb.operation.importer.ImportHelper;

import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.stream.Collectors;

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
        Long intValue = attribute.getIntValue().orElse(null);
        if (intValue != null) {
            stmt.setLong(7, intValue);
        } else {
            stmt.setNull(7, Types.BIGINT);
        }

        Double doubleValue = attribute.getDoubleValue().orElse(null);
        if (doubleValue != null) {
            stmt.setDouble(8, doubleValue);
        } else {
            stmt.setNull(8, Types.DOUBLE);
        }

        stmt.setString(9, attribute.getStringValue().orElse(null));

        OffsetDateTime timestamp = attribute.getTimeStamp().orElse(null);
        if (timestamp != null) {
            stmt.setObject(10, timestamp, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            stmt.setNull(10, Types.TIMESTAMP_WITH_TIMEZONE);
        }

        stmt.setString(11, attribute.getURI().orElse(null));
        stmt.setString(12, attribute.getCodeSpace().orElse(null));
        stmt.setString(13, attribute.getUom().orElse(null));

        String arrayValue = attribute.getArrayValue()
                .map(array -> new JSONArray(array.getValues().stream()
                        .map(Value::rawValue)
                        .collect(Collectors.toList())).toString())
                .orElse(null);
        if (arrayValue != null) {
            stmt.setObject(14, arrayValue, Types.OTHER);
        } else {
            stmt.setNull(14, Types.OTHER);
        }

        stmt.setString(15, attribute.getGenericContent().orElse(null));
        stmt.setString(16, attribute.getGenericContentMimeType().orElse(null));

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
