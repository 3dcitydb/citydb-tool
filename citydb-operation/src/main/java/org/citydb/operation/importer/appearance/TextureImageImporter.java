/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.importer.appearance;

import org.citydb.core.file.FileLocator;
import org.citydb.database.schema.Sequence;
import org.citydb.database.schema.Table;
import org.citydb.model.common.ExternalFile;
import org.citydb.operation.importer.ImportHelper;
import org.citydb.operation.importer.common.DatabaseImporter;
import org.citydb.operation.importer.reference.CacheType;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;

public class TextureImageImporter extends DatabaseImporter {

    public TextureImageImporter(ImportHelper helper) throws SQLException {
        super(Table.TEX_IMAGE, helper);
    }

    @Override
    protected String getInsertStatement() {
        return "insert into " + tableHelper.getPrefixedTableName(table) +
                "(id, image_uri, image_data, mime_type, mime_type_codespace) " +
                "values (" + String.join(",", Collections.nCopies(5, "?")) + ")";
    }

    public long doImport(ExternalFile textureImage) throws IOException, SQLException {
        long texImageId = nextSequenceValue(Sequence.TEX_IMAGE);
        FileLocator locator = getFileLocator(textureImage);

        stmt.setLong(1, texImageId);
        stmt.setString(2, locator.getFileName());
        stmt.setBytes(3, getBytes(locator));
        setStringOrNull(4, textureImage.getMimeType().orElse(null));
        setStringOrNull(5, textureImage.getMimeTypeCodeSpace().orElse(null));

        stmt.execute();
        cacheTarget(CacheType.TEXTURE_IMAGE, textureImage.getObjectId().orElse(null), texImageId);

        return texImageId;
    }

    @Override
    public void executeBatch() throws SQLException {
    }
}
