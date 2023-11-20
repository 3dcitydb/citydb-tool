/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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

package org.citydb.operation.exporter.common;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONReader;
import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.geometry.GeometryException;
import org.citydb.database.schema.DataTypeHelper;
import org.citydb.database.schema.NamespaceHelper;
import org.citydb.database.schema.ObjectClassHelper;
import org.citydb.model.appearance.Color;
import org.citydb.model.common.Name;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.property.ArrayValue;
import org.citydb.model.property.DataType;
import org.citydb.operation.exporter.ExportException;
import org.citydb.operation.exporter.ExportHelper;
import org.citydb.operation.exporter.util.TableHelper;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public abstract class DatabaseExporter {
    protected final ExportHelper helper;
    protected final DatabaseAdapter adapter;
    protected final DataTypeHelper dataTypeHelper;
    protected final NamespaceHelper namespaceHelper;
    protected final ObjectClassHelper objectClassHelper;
    protected final TableHelper tableHelper;

    protected PreparedStatement stmt;

    public DatabaseExporter(ExportHelper helper) {
        this.helper = helper;
        this.adapter = helper.getAdapter();
        this.dataTypeHelper = helper.getDataTypeHelper();
        this.namespaceHelper = helper.getNamespaceHelper();
        this.objectClassHelper = helper.getObjectClassHelper();
        this.tableHelper = helper.getTableHelper();
    }

    protected Long getLong(String column, ResultSet rs) throws SQLException {
        long value = rs.getLong(column);
        return !rs.wasNull() ? value : null;
    }

    protected Double getDouble(String column, ResultSet rs) throws SQLException {
        double value = rs.getDouble(column);
        return !rs.wasNull() ? value : null;
    }

    protected Boolean getBoolean(String column, ResultSet rs) throws SQLException {
        int value = rs.getInt(column);
        return !rs.wasNull() ? value == 1 : null;
    }

    protected JSONObject getJSONObject(String content) {
        return JSON.parseObject(content, JSONReader.Feature.UseBigDecimalForDoubles);
    }

    protected JSONArray getJSONArray(String content) {
        return JSON.parseArray(content, JSONReader.Feature.UseBigDecimalForDoubles);
    }

    protected Name getName(String nameColumn, String namespaceIdColumn, ResultSet rs) throws SQLException {
        String localName = rs.getString(nameColumn);
        if (!rs.wasNull()) {
            int namespaceId = rs.getInt(namespaceIdColumn);
            return !rs.wasNull() ?
                    Name.of(localName, namespaceHelper.getNamespace(namespaceId)) :
                    Name.of(localName);
        }

        return null;
    }

    protected DataType getDataType(String column, ResultSet rs) throws SQLException {
        int dataTypeId = rs.getInt(column);
        return !rs.wasNull() ?
                DataType.of(dataTypeHelper.getDataType(dataTypeId)) :
                null;
    }

    protected ArrayValue getArrayValue(String content) {
        if (content != null) {
            JSONArray json = getJSONArray(content);
            if (json != null) {
                return ArrayValue.ofList(json);
            }
        }

        return null;
    }

    protected Color getColor(String rgba) {
        try {
            return Color.ofHexString(rgba);
        } catch (Exception e) {
            return null;
        }
    }

    protected Geometry<?> getGeometry(Object geometryObject) throws ExportException {
        try {
            return geometryObject != null ?
                    adapter.getGeometryAdapter().getGeometry(geometryObject)
                            .setSRID(helper.getSRID())
                            .setSrsName(helper.getSrsName()) :
                    null;
        } catch (GeometryException e) {
            throw new ExportException("Failed to convert database geometry.", e);
        }
    }

    protected <T extends Geometry<?>> T getGeometry(Object geometryObject, Class<T> type) throws ExportException {
        Geometry<?> geometry = getGeometry(geometryObject);
        return type.isInstance(geometry) ? type.cast(geometry) : null;
    }

    protected Envelope getEnvelope(Object geometryObject) throws ExportException {
        Geometry<?> geometry = getGeometry(geometryObject);
        return geometry != null ? geometry.getEnvelope() : null;
    }

    public void close() throws ExportException, SQLException {
        if (stmt != null) {
            stmt.close();
        }
    }
}
