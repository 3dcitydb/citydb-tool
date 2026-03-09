/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.appearance;

import org.citydb.model.appearance.SurfaceData;
import org.citydb.operation.exporter.ExportHelper;
import org.citydb.operation.exporter.common.DatabaseExporter;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SurfaceDataMappingExporter extends DatabaseExporter {

    public SurfaceDataMappingExporter(ExportHelper helper) {
        super(helper);
    }

    protected void doExport(SurfaceData<?> surfaceData, ResultSet rs) throws SQLException {
        long geometryDataId = rs.getLong("geometry_data_id");
        helper.getSurfaceDataMapper()
                .buildMaterialMapping(getJSONObject(rs.getString("material_mapping")), geometryDataId, surfaceData)
                .buildTextureMapping(getJSONObject(rs.getString("texture_mapping")), geometryDataId, surfaceData)
                .buildWorldToTextureMapping(getJSONObject(rs.getString("world_to_texture_mapping")), geometryDataId, surfaceData)
                .buildGeoreferencedTextureMapping(getJSONObject(rs.getString("georeferenced_texture_mapping")), geometryDataId, surfaceData);
    }
}
