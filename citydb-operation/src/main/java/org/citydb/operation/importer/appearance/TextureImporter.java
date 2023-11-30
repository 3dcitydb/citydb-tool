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

import org.citydb.model.appearance.*;
import org.citydb.model.common.ExternalFile;
import org.citydb.operation.importer.ImportException;
import org.citydb.operation.importer.ImportHelper;
import org.citydb.operation.importer.reference.CacheType;

import java.sql.SQLException;
import java.sql.Types;

public abstract class TextureImporter extends SurfaceDataImporter {

    public TextureImporter(ImportHelper helper) throws SQLException {
        super(helper);
    }

    long doImport(Texture<?> texture, long surfaceDataId) throws ImportException, SQLException {
        TextureImageProperty imageProperty = texture.getTextureImageProperty().orElse(null);
        if (imageProperty != null) {
            ExternalFile textureImage = imageProperty.getObject().orElse(null);
            if (textureImage != null) {
                stmt.setLong(7, tableHelper.getOrCreateImporter(TextureImageImporter.class)
                        .doImport(textureImage));
            } else if (imageProperty.getReference().isPresent()) {
                cacheReference(CacheType.TEXTURE_IMAGE, imageProperty.getReference().get(), surfaceDataId);
                stmt.setNull(7, Types.BIGINT);
            }
        } else {
            stmt.setNull(7, Types.BIGINT);
        }

        stmt.setString(8, texture.getTextureType().map(TextureType::getDatabaseValue).orElse(null));
        stmt.setString(9, texture.getWrapMode().map(WrapMode::getDatabaseValue).orElse(null));
        stmt.setString(10, texture.getBorderColor().map(Color::toRGBA).orElse(null));

        return super.doImport(texture, surfaceDataId);
    }
}
