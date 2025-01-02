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

package org.citydb.operation.importer.appearance;

import com.alibaba.fastjson2.JSONArray;
import org.citydb.database.schema.Sequence;
import org.citydb.model.appearance.GeoreferencedTexture;
import org.citydb.model.geometry.Point;
import org.citydb.model.util.matrix.Matrix;
import org.citydb.operation.importer.ImportException;
import org.citydb.operation.importer.ImportHelper;

import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;

public class GeoreferencedTextureImporter extends TextureImporter {

    public GeoreferencedTextureImporter(ImportHelper helper) throws SQLException {
        super(helper);
    }

    @Override
    protected String getInsertStatement() {
        return "insert into " + tableHelper.getPrefixedTableName(table) +
                "(id, objectid, identifier, identifier_codespace, is_front, objectclass_id, " +
                "tex_image_id, tex_texture_type, tex_wrap_mode, tex_border_color, " +
                "gt_orientation, gt_reference_point) " +
                "values (" + String.join(",", Collections.nCopies(12, "?")) + ")";
    }

    public long doImport(GeoreferencedTexture texture) throws ImportException, SQLException {
        long surfaceDataId = nextSequenceValue(Sequence.SURFACE_DATA);

        String orientation = texture.getOrientation()
                .map(Matrix::toRowMajor)
                .map(JSONArray::new)
                .map(JSONArray::toString)
                .orElse(null);
        if (orientation != null) {
            stmt.setObject(11, orientation, Types.OTHER);
        } else {
            stmt.setNull(11, Types.OTHER);
        }

        Object referencePoint = getGeometry(texture.getReferencePoint()
                .map(Point::force2D)
                .orElse(null), false);
        if (referencePoint != null) {
            stmt.setObject(12, referencePoint, adapter.getGeometryAdapter().getGeometrySqlType());
        } else {
            stmt.setNull(12, adapter.getGeometryAdapter().getGeometrySqlType(),
                    adapter.getGeometryAdapter().getGeometryTypeName());
        }

        return doImport(texture, surfaceDataId);
    }
}
