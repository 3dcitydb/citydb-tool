/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
import java.util.*;

public class GeometryExporter extends DatabaseExporter {

    public GeometryExporter(ExportHelper helper) throws SQLException {
        super(helper);
        stmt = helper.getConnection().prepareStatement(getQuery().toSql());
    }

    private Select getQuery() {
        Table geometryData = tableHelper.getTable(org.citydb.database.schema.Table.GEOMETRY_DATA);
        return Select.newInstance()
                .select(geometryData.columns("id", "implicit_geometry", "geometry_properties", "feature_id"))
                .select(helper.getTransformOperator(geometryData.column("geometry")))
                .from(geometryData)
                .where(operationHelper.inArray(geometryData.column("id"), Placeholder.empty()));
    }

    public Geometry<?> doExport(long id) throws ExportException, SQLException {
        return doExport(id, false);
    }

    public Geometry<?> doExport(long id, boolean isImplicit) throws ExportException, SQLException {
        setLongArrayOrNull(1, List.of(id));
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
        if (!ids.isEmpty()) {
            setLongArrayOrNull(1, ids);
            try (ResultSet rs = stmt.executeQuery()) {
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
