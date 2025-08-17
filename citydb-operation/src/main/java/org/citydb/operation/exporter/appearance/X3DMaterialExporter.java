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

package org.citydb.operation.exporter.appearance;

import org.citydb.model.appearance.X3DMaterial;
import org.citydb.operation.exporter.ExportHelper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class X3DMaterialExporter extends SurfaceDataExporter {

    public X3DMaterialExporter(ExportHelper helper) {
        super(helper);
    }

    protected X3DMaterial doExport(ResultSet rs) throws SQLException {
        X3DMaterial material = X3DMaterial.newInstance()
                .setShininess(getDouble("x3d_shininess", rs))
                .setTransparency(getDouble("x3d_transparency", rs))
                .setAmbientIntensity(getDouble("x3d_ambient_intensity", rs))
                .setDiffuseColor(getColor(rs.getString("x3d_diffuse_color")))
                .setEmissiveColor(getColor(rs.getString("x3d_emissive_color")))
                .setSpecularColor(getColor(rs.getString("x3d_specular_color")))
                .setIsSmooth(getBoolean("x3d_is_smooth", rs));

        return doExport(material, rs);
    }
}
