/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.appearance;

import com.alibaba.fastjson2.JSONArray;
import org.citydb.model.appearance.GeoreferencedTexture;
import org.citydb.model.geometry.Point;
import org.citydb.operation.exporter.ExportException;
import org.citydb.operation.exporter.ExportHelper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.stream.IntStream;

public class GeoreferencedTextureExporter extends TextureExporter {

    public GeoreferencedTextureExporter(ExportHelper helper) throws SQLException {
        super(helper);
    }

    protected GeoreferencedTexture doExport(ResultSet rs) throws ExportException, SQLException {
        GeoreferencedTexture texture = GeoreferencedTexture.newInstance();

        JSONArray orientation = getJSONArray(rs.getString("gt_orientation"));
        if (orientation != null) {
            texture.setOrientation(IntStream.range(0, orientation.size())
                    .mapToObj(orientation::getDouble)
                    .toList());
        }

        Point referencePoint = getGeometry(rs.getObject("gt_reference_point"), Point.class);
        if (referencePoint != null) {
            texture.setReferencePoint(referencePoint.force2D());
        }

        return doExport(texture, rs);
    }
}
