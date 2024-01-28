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
