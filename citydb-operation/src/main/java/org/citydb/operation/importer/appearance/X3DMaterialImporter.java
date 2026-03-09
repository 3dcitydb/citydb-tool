/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.importer.appearance;

import org.citydb.database.schema.Sequence;
import org.citydb.model.appearance.Color;
import org.citydb.model.appearance.X3DMaterial;
import org.citydb.operation.importer.ImportException;
import org.citydb.operation.importer.ImportHelper;

import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;

public class X3DMaterialImporter extends SurfaceDataImporter {

    public X3DMaterialImporter(ImportHelper helper) throws SQLException {
        super(helper);
    }

    @Override
    protected String getInsertStatement() {
        return "insert into " + tableHelper.getPrefixedTableName(table) +
                "(id, objectid, identifier, identifier_codespace, is_front, objectclass_id, " +
                "x3d_shininess, x3d_transparency, x3d_ambient_intensity, x3d_specular_color, x3d_diffuse_color, " +
                "x3d_emissive_color, x3d_is_smooth) " +
                "values (" + String.join(",", Collections.nCopies(13, "?")) + ")";
    }

    public long doImport(X3DMaterial material) throws ImportException, SQLException {
        long surfaceDataId = nextSequenceValue(Sequence.SURFACE_DATA);

        Double shininess = material.getShininess().orElse(null);
        if (shininess != null) {
            stmt.setDouble(7, shininess);
        } else {
            stmt.setNull(7, Types.DOUBLE);
        }

        Double transparency = material.getTransparency().orElse(null);
        if (transparency != null) {
            stmt.setDouble(8, transparency);
        } else {
            stmt.setNull(8, Types.DOUBLE);
        }

        Double ambientIntensity = material.getAmbientIntensity().orElse(null);
        if (ambientIntensity != null) {
            stmt.setDouble(9, ambientIntensity);
        } else {
            stmt.setNull(9, Types.DOUBLE);
        }

        stmt.setString(10, material.getSpecularColor().map(Color::toRGB).orElse(null));
        stmt.setString(11, material.getDiffuseColor().map(Color::toRGB).orElse(null));
        stmt.setString(12, material.getEmissiveColor().map(Color::toRGB).orElse(null));

        Integer isSmooth = material.getIsSmooth().map(v -> v ? 1 : 0).orElse(null);
        if (isSmooth != null) {
            stmt.setInt(13, isSmooth);
        } else {
            stmt.setNull(13, Types.INTEGER);
        }

        return super.doImport(material, surfaceDataId);
    }
}
