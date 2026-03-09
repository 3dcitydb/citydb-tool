/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.importer.geometry;

import org.citydb.core.file.FileLocator;
import org.citydb.database.schema.Sequence;
import org.citydb.database.schema.Table;
import org.citydb.model.common.ExternalFile;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.property.AppearanceProperty;
import org.citydb.operation.importer.ImportException;
import org.citydb.operation.importer.ImportHelper;
import org.citydb.operation.importer.appearance.AppearanceImporter;
import org.citydb.operation.importer.common.DatabaseImporter;
import org.citydb.operation.importer.reference.CacheType;
import org.slf4j.event.Level;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;

public class ImplicitGeometryImporter extends DatabaseImporter {

    public ImplicitGeometryImporter(ImportHelper helper) throws SQLException {
        super(Table.IMPLICIT_GEOMETRY, helper);
    }

    @Override
    protected String getInsertStatement() {
        return "insert into " + tableHelper.getPrefixedTableName(table) +
                "(id, objectid, mime_type, mime_type_codespace, reference_to_library, library_object, " +
                "relative_geometry_id) " +
                "values (" + String.join(",", Collections.nCopies(7, "?")) + ")";
    }

    public long doImport(ImplicitGeometry implicitGeometry, long featureId) throws ImportException, SQLException {
        String objectId = implicitGeometry.getOrCreateObjectId();
        long implicitGeometryId = nextSequenceValue(Sequence.IMPLICIT_GEOMETRY);

        stmt.setLong(1, implicitGeometryId);
        stmt.setString(2, objectId);

        if (implicitGeometry.getGeometry().isPresent()) {
            stmt.setNull(3, Types.VARCHAR);
            stmt.setNull(4, Types.VARCHAR);
            stmt.setNull(5, Types.VARCHAR);
            setBytesOrNull(6, null);
            stmt.setLong(7, tableHelper.getOrCreateImporter(GeometryImporter.class)
                    .doImport(implicitGeometry.getGeometry().get(), true, featureId)
                    .getId());
        } else if (implicitGeometry.getLibraryObject().isPresent()) {
            ExternalFile libraryObject = implicitGeometry.getLibraryObject().get();
            FileLocator locator = getFileLocator(libraryObject);

            setStringOrNull(3, libraryObject.getMimeType().orElse(null));
            setStringOrNull(4, libraryObject.getMimeTypeCodeSpace().orElse(null));
            stmt.setString(5, locator.getFileName());

            byte[] bytes = null;
            try {
                bytes = getBytes(locator);
            } catch (IOException e) {
                logOrThrow(Level.ERROR, formatMessage(implicitGeometry,
                        "Failed to import library object " + libraryObject.getFileLocation() + "."), e);
            }

            setBytesOrNull(6, bytes);
            stmt.setNull(7, Types.BIGINT);
        }

        addBatch();
        cacheTarget(CacheType.IMPLICIT_GEOMETRY, objectId, implicitGeometryId);

        if (implicitGeometry.hasAppearances()) {
            for (AppearanceProperty property : implicitGeometry.getAppearances().getAll()) {
                tableHelper.getOrCreateImporter(AppearanceImporter.class)
                        .doImport(property.getObject(), implicitGeometryId, AppearanceImporter.Type.IMPLICIT_GEOMETRY);
            }
        }

        return implicitGeometryId;
    }
}
