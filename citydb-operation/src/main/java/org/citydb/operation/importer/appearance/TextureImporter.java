/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.importer.appearance;

import org.citydb.model.appearance.Color;
import org.citydb.model.appearance.Texture;
import org.citydb.model.appearance.TextureType;
import org.citydb.model.appearance.WrapMode;
import org.citydb.model.common.ExternalFile;
import org.citydb.operation.importer.ImportException;
import org.citydb.operation.importer.ImportHelper;
import org.citydb.operation.importer.reference.CacheType;
import org.slf4j.event.Level;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Types;

public abstract class TextureImporter extends SurfaceDataImporter {

    public TextureImporter(ImportHelper helper) throws SQLException {
        super(helper);
    }

    long doImport(Texture<?> texture, long surfaceDataId) throws ImportException, SQLException {
        ExternalFile textureImage = texture.getTextureImage().orElse(null);
        if (textureImage != null) {
            if (canImport(textureImage)) {
                try {
                    stmt.setLong(7, tableHelper.getOrCreateImporter(TextureImageImporter.class)
                            .doImport(textureImage));
                } catch (IOException e) {
                    logOrThrow(Level.ERROR, formatMessage(texture,
                            "Failed to import texture file " + textureImage.getFileLocation() + "."), e);
                    stmt.setNull(7, Types.BIGINT);
                }
            } else {
                cacheReference(CacheType.TEXTURE_IMAGE, textureImage.getOrCreateObjectId(), surfaceDataId);
                stmt.setNull(7, Types.BIGINT);
            }
        } else {
            stmt.setNull(7, Types.BIGINT);
        }

        setStringOrNull(8, texture.getTextureType().map(TextureType::getDatabaseValue).orElse(null));
        setStringOrNull(9, texture.getWrapMode().map(WrapMode::getDatabaseValue).orElse(null));
        setStringOrNull(10, texture.getBorderColor().map(Color::toRGBA).orElse(null));

        return super.doImport(texture, surfaceDataId);
    }
}
