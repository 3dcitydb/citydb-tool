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

import org.citydb.core.file.FileLocator;
import org.citydb.database.schema.Sequence;
import org.citydb.database.schema.Table;
import org.citydb.model.common.ExternalFile;
import org.citydb.operation.importer.ImportException;
import org.citydb.operation.importer.ImportHelper;
import org.citydb.operation.importer.common.DatabaseImporter;
import org.citydb.operation.importer.reference.CacheType;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Collections;

public class TextureImageImporter extends DatabaseImporter {

    public TextureImageImporter(ImportHelper helper) throws SQLException {
        super(Table.TEX_IMAGE, helper);
    }

    @Override
    protected String getInsertStatement() {
        return "insert into " + tableHelper.getPrefixedTableName(table)  +
                "(id, image_uri, image_data, mime_type, mime_type_codespace) " +
                "values (" + String.join(",", Collections.nCopies(5, "?")) + ")";
    }

    public long doImport(ExternalFile textureImage) throws ImportException, SQLException {
        long texImageId = nextSequenceValue(Sequence.TEX_IMAGE);
        FileLocator locator = getFileLocator(textureImage);

        stmt.setLong(1, texImageId);
        stmt.setString(2, locator.getFileName());

        try (InputStream stream = locator.openStream()) {
            stmt.setBytes(3, stream.readAllBytes());
        } catch (IOException e) {
            throw new ImportException("Failed to load texture file " + textureImage.getFileLocation() + ".", e);
        }

        stmt.setString(4, textureImage.getMimeType().orElse(null));
        stmt.setString(5, textureImage.getMimeTypeCodeSpace().orElse(null));

        addBatch();
        cacheTarget(CacheType.TEXTURE_IMAGE, textureImage.getObjectId().orElse(null), texImageId);

        return texImageId;
    }
}
