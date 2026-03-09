/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
            setJsonOrNull(3, getJson(mapping.getMaterialMapping(), JSONWriter.Feature.LargeObject));
            setJsonOrNull(4, getJson(mapping.getTextureMapping(), JSONWriter.Feature.LargeObject));
            setJsonOrNull(5, getJson(mapping.getWorldToTextureMapping(), JSONWriter.Feature.LargeObject));
            setJsonOrNull(6, getJson(mapping.getGeoreferencedTextureMapping(), JSONWriter.Feature.LargeObject));

            addBatch();
        }
    }
}
