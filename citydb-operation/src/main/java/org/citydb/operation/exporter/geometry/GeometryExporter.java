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

package org.citydb.operation.exporter.geometry;

import com.alibaba.fastjson2.JSONObject;
import org.citydb.database.geometry.GeometryException;
import org.citydb.database.schema.Table;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.geometry.GeometryDescriptor;
import org.citydb.operation.exporter.ExportException;
import org.citydb.operation.exporter.ExportHelper;
import org.citydb.operation.exporter.common.DatabaseExporter;

import java.sql.ResultSet;
import java.sql.SQLException;

public class GeometryExporter extends DatabaseExporter {

    public GeometryExporter(ExportHelper helper) throws SQLException {
        super(helper);
        stmt = helper.getConnection().prepareStatement("select " + helper.getTransformOperator("geometry") +
                ", implicit_geometry, geometry_properties, feature_id as geometry_feature_id " +
                "from " + tableHelper.getPrefixedTableName(Table.GEOMETRY_DATA) +
                " where id = ?");
    }

    public Geometry<?> doExport(long id) throws ExportException, SQLException {
        return doExport(id, false);
    }

    public Geometry<?> doExport(long id, boolean isImplicit) throws ExportException, SQLException {
        stmt.setLong(1, id);
        try (ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return doExport(id, isImplicit, rs);
            }
        }

        return null;
    }

    public Geometry<?> doExport(long id, boolean isImplicit, ResultSet rs) throws ExportException, SQLException {
        Object geometryObject = isImplicit ?
                rs.getObject("implicit_geometry") :
                rs.getObject("geometry");
        JSONObject properties = getJSONObject(rs.getString("geometry_properties"));
        if (geometryObject != null && properties != null) {
            try {
                Geometry<?> geometry = adapter.getGeometryAdapter().buildGeometry(geometryObject, properties);
                if (geometry != null) {
                    if (!isImplicit) {
                        geometry.setSRID(helper.getSRID())
                                .setSrsIdentifier(helper.getSrsIdentifier());
                    }

                    return geometry.setDescriptor(GeometryDescriptor.of(id, rs.getLong("geometry_feature_id")));
                }
            } catch (GeometryException e) {
                throw new ExportException("Failed to export geometry (ID: " + id + ").", e);
            }
        }

        return null;
    }
}
