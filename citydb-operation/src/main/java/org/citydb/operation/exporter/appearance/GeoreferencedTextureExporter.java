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
