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

package org.citydb.operation.exporter.geometry;

import com.alibaba.fastjson2.JSONObject;
import org.citydb.database.geometry.GeometryException;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.geometry.GeometryDescriptor;
import org.citydb.operation.exporter.ExportException;
import org.citydb.operation.exporter.ExportHelper;
import org.citydb.operation.exporter.common.DatabaseExporter;
import org.citydb.sqlbuilder.literal.Placeholder;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Table;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GeometryExporter extends DatabaseExporter {
    private final Table geometryData;
    private final Select select;

    public GeometryExporter(ExportHelper helper) throws SQLException {
        super(helper);
        geometryData = tableHelper.getTable(org.citydb.database.schema.Table.GEOMETRY_DATA);
        select = getBaseQuery();
        stmt = helper.getConnection().prepareStatement(Select.of(select)
                .where(geometryData.column("id").eq(Placeholder.empty()))
                .toSql());
    }

    private Select getBaseQuery() {
        return Select.newInstance()
                .select(geometryData.columns("id", "implicit_geometry", "geometry_properties", "feature_id"))
                .select(helper.getTransformOperator(geometryData.column("geometry")))
                .from(geometryData);
    }

    private Select getQuery(Set<Long> ids) {
        return Select.of(select)
                .where(operationHelper.in(geometryData.column("id"), ids));
    }

    public Geometry<?> doExport(long id) throws ExportException, SQLException {
        return doExport(id, false);
    }

    public Geometry<?> doExport(long id, boolean isImplicit) throws ExportException, SQLException {
        stmt.setLong(1, id);
        try (ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return doExport(isImplicit, rs).get(id);
            }
        }

        return null;
    }

    public Map<Long, Geometry<?>> doExport(Set<Long> ids) throws ExportException, SQLException {
        return doExport(ids, false);
    }

    public Map<Long, Geometry<?>> doExport(Set<Long> ids, boolean isImplicit) throws ExportException, SQLException {
        if (ids.size() == 1) {
            stmt.setLong(1, ids.iterator().next());
            try (ResultSet rs = stmt.executeQuery()) {
                return doExport(isImplicit, rs);
            }
        } else if (!ids.isEmpty()) {
            try (Statement stmt = helper.getConnection().createStatement();
                 ResultSet rs = stmt.executeQuery(getQuery(ids).toSql())) {
                return doExport(isImplicit, rs);
            }
        } else {
            return Collections.emptyMap();
        }
    }

    private Map<Long, Geometry<?>> doExport(boolean isImplicit, ResultSet rs) throws ExportException, SQLException {
        Map<Long, Geometry<?>> geometries = new HashMap<>();
        while (rs.next()) {
            long id = rs.getLong("id");
            geometries.put(id, doExport(id, isImplicit, rs));
        }

        return geometries;
    }

    Geometry<?> doExport(long id, boolean isImplicit, ResultSet rs) throws ExportException, SQLException {
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

                    return geometry.setDescriptor(GeometryDescriptor.of(id, rs.getLong("feature_id")));
                }
            } catch (GeometryException e) {
                throw new ExportException("Failed to export geometry (ID: " + id + ").", e);
            }
        }

        return null;
    }
}
