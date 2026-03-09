/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
