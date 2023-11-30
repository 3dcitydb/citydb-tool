/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

import org.citydb.database.schema.Sequence;
import org.citydb.model.appearance.ParameterizedTexture;
import org.citydb.operation.importer.ImportException;
import org.citydb.operation.importer.ImportHelper;

import java.sql.SQLException;
import java.util.Collections;

public class ParameterizedTextureImporter extends TextureImporter {

    public ParameterizedTextureImporter(ImportHelper helper) throws SQLException {
        super(helper);
    }

    @Override
    protected String getInsertStatement() {
        return "insert into " + tableHelper.getPrefixedTableName(table) +
                "(id, objectid, identifier, identifier_codespace, is_front, objectclass_id, " +
                "tex_image_id, tex_texture_type, tex_wrap_mode, tex_border_color) " +
                "values (" + String.join(",", Collections.nCopies(10, "?")) + ")";
    }

    public long doImport(ParameterizedTexture texture) throws ImportException, SQLException {
        return super.doImport(texture, nextSequenceValue(Sequence.SURFACE_DATA));
    }
}
