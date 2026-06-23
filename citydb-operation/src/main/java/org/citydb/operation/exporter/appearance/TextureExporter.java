/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.appearance;

import org.citydb.database.schema.Table;
import org.citydb.model.appearance.Texture;
import org.citydb.model.appearance.TextureType;
import org.citydb.model.appearance.WrapMode;
import org.citydb.model.common.ExternalFile;
import org.citydb.operation.exporter.ExportConstants;
import org.citydb.operation.exporter.ExportException;
import org.citydb.operation.exporter.ExportHelper;
import org.citydb.operation.exporter.common.BlobExporter;
import org.citydb.operation.exporter.options.AppearanceOptions;
import org.citydb.operation.exporter.util.ExternalFileHelper;

import java.sql.ResultSet;
import java.sql.SQLException;

public abstract class TextureExporter extends SurfaceDataExporter {
    private final BlobExporter blobExporter;
    private final ExternalFileHelper externalFileHelper;

    public TextureExporter(ExportHelper helper) throws SQLException {
        super(helper);
        blobExporter = new BlobExporter(tableHelper.getTable(Table.TEX_IMAGE), "id", "image_data", helper);
        externalFileHelper = ExternalFileHelper.newInstance(helper)
                .withRelativeOutputFolder(ExportConstants.TEXTURE_DIR)
                .withFileNamePrefix(ExportConstants.TEXTURE_PREFIX)
                .createUniqueFileNames(true)
                .withNumberOfBuckets(helper.getOptions().getAppearanceOptions()
                        .orElseGet(AppearanceOptions::new)
                        .getNumberOfTextureBuckets());
    }

    protected <T extends Texture<?>> T doExport(T texture, ResultSet rs) throws ExportException, SQLException {
        texture.setBorderColor(getColor(rs.getString("tex_border_color")))
                .setWrapMode(WrapMode.fromDatabaseValue(rs.getString("tex_wrap_mode")))
                .setTextureType(TextureType.fromDatabaseValue(rs.getString("tex_texture_type")));

        long texImageId = rs.getLong("tex_image_id");
        if (!rs.wasNull()) {
            String imageURI = rs.getString("image_uri");
            String mimeType = rs.getString("mime_type");

            ExternalFile textureImage = externalFileHelper.createExternalFile(texImageId, imageURI, mimeType);
            if (textureImage != null) {
                texture.setTextureImage(textureImage
                        .setMimeType(mimeType)
                        .setMimeTypeCodeSpace(rs.getString("mime_type_codespace")));

                if (!helper.lookupAndPut(textureImage)) {
                    blobExporter.addBatch(texImageId, textureImage);
                }
            }
        }

        return super.doExport(texture, rs);
    }

    @Override
    public void close() throws ExportException, SQLException {
        super.close();
        blobExporter.close();
    }
}
