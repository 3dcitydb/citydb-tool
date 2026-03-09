/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.property;

import org.citydb.model.common.Name;
import org.citydb.model.geometry.Point;
import org.citydb.model.property.PropertyDescriptor;
import org.citydb.model.property.RelationType;
import org.citydb.operation.exporter.ExportException;
import org.citydb.operation.exporter.ExportHelper;
import org.citydb.operation.exporter.common.DatabaseExporter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

public class PropertyExporter extends DatabaseExporter {

    public PropertyExporter(ExportHelper helper) {
        super(helper);
    }

    public PropertyStub doExport(long featureId, ResultSet rs) throws ExportException, SQLException {
        Name name = getName("name", "namespace_id", rs);
        if (name != null) {
            return PropertyStub.of(name)
                    .setDataType(getDataType("datatype_id", rs))
                    .setIntValue(getLong("val_int", rs))
                    .setDoubleValue(getDouble("val_double", rs))
                    .setStringValue(rs.getString("val_string"))
                    .setTimeStamp(rs.getObject("val_timestamp", OffsetDateTime.class))
                    .setURI(rs.getString("val_uri"))
                    .setCodeSpace(rs.getString("val_codespace"))
                    .setUom(rs.getString("val_uom"))
                    .setArrayValue(getArrayValue(rs.getString("val_array")))
                    .setLod(rs.getString("val_lod"))
                    .setGeometryId(getLong("val_geometry_id", rs))
                    .setImplicitGeometryId(getLong("val_implicitgeom_id", rs))
                    .setReferencePoint(getGeometry(rs.getObject("val_implicitgeom_refpoint"), Point.class))
                    .setAppearanceId(getLong("val_appearance_id", rs))
                    .setAddressId(getLong("val_address_id", rs))
                    .setFeatureId(getLong("val_feature_id", rs))
                    .setRelationType(RelationType.fromDatabaseValue(rs.getInt("val_relation_type")))
                    .setGenericContent(rs.getString("val_content"))
                    .setGenericContentMimeType(rs.getString("val_content_mime_type"))
                    .setDescriptor(PropertyDescriptor.of(rs.getLong("id"), featureId)
                            .setParentId(rs.getLong("parent_id")));
        } else {
            return null;
        }
    }
}
