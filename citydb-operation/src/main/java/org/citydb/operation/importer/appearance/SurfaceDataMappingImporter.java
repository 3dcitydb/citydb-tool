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

package org.citydb.operation.importer.appearance;

import com.alibaba.fastjson2.JSONWriter;
import org.citydb.database.schema.Table;
import org.citydb.model.appearance.SurfaceData;
import org.citydb.operation.importer.ImportException;
import org.citydb.operation.importer.ImportHelper;
import org.citydb.operation.importer.common.DatabaseImporter;
import org.citydb.operation.importer.util.SurfaceDataMapper;
import org.citydb.operation.importer.util.SurfaceDataMapping;

import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;
import java.util.Map;

public class SurfaceDataMappingImporter extends DatabaseImporter {
    private final SurfaceDataMapper mapper = SurfaceDataMapper.newInstance();

    public SurfaceDataMappingImporter(ImportHelper helper) throws SQLException {
        super(Table.SURFACE_DATA_MAPPING, helper);
    }

    @Override
    protected String getInsertStatement() {
        return "insert into " + tableHelper.getPrefixedTableName(table) +
                "(surface_data_id, geometry_data_id, material_mapping, texture_mapping, world_to_texture_mapping, " +
                "georeferenced_texture_mapping) values (" + String.join(",", Collections.nCopies(6, "?")) + ")";
    }

    public void doImport(SurfaceData<?> surfaceData, long surfaceDataId) throws ImportException, SQLException {
        Map<Long, SurfaceDataMapping> mappings = mapper.createMapping(surfaceData);
        for (Map.Entry<Long, SurfaceDataMapping> entry : mappings.entrySet()) {
            SurfaceDataMapping mapping = entry.getValue();

            stmt.setLong(1, surfaceDataId);
            stmt.setLong(2, entry.getKey());

            if (mapping.hasMaterialMapping()) {
                stmt.setObject(3, mapping.getMaterialMapping()
                        .toString(JSONWriter.Feature.LargeObject), adapter.getSchemaAdapter().getOtherSqlType());
            } else {
                stmt.setNull(3, adapter.getSchemaAdapter().getOtherSqlType());
            }

            if (mapping.hasTextureMapping()) {
                stmt.setObject(4, mapping.getTextureMapping()
                        .toString(JSONWriter.Feature.LargeObject), adapter.getSchemaAdapter().getOtherSqlType());
            } else {
                stmt.setNull(4, adapter.getSchemaAdapter().getOtherSqlType());
            }

            if (mapping.hasWorldToTextureMapping()) {
                stmt.setObject(5, mapping.getWorldToTextureMapping()
                        .toString(JSONWriter.Feature.LargeObject), adapter.getSchemaAdapter().getOtherSqlType());
            } else {
                stmt.setNull(5, adapter.getSchemaAdapter().getOtherSqlType());
            }

            if (mapping.hasGeoreferencedTextureMapping()) {
                stmt.setObject(6, mapping.getGeoreferencedTextureMapping()
                        .toString(JSONWriter.Feature.LargeObject), adapter.getSchemaAdapter().getOtherSqlType());
            } else {
                stmt.setNull(6, adapter.getSchemaAdapter().getOtherSqlType());
            }

            addBatch();
        }
    }
}
