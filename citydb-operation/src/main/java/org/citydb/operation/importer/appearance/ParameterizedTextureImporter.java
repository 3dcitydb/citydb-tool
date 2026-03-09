/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
