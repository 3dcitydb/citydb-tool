/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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

import java.io.IOException;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;

public class ImplicitGeometryImporter extends DatabaseImporter {
    private final PreparedStatement lookupImplicitGeometry;

    public ImplicitGeometryImporter(ImportHelper helper) throws SQLException {
        super(Table.IMPLICIT_GEOMETRY, helper);
        lookupImplicitGeometry = helper.getConnection().prepareStatement("select id from " +
                tableHelper.getPrefixedTableName(table) +
                " where objectid = ? fetch first 1 rows only");
    }

    @Override
    protected String getInsertStatement() {
        return "insert into " + tableHelper.getPrefixedTableName(table) +
                "(id, objectid, mime_type, mime_type_codespace, reference_to_library, library_object, " +
                "relative_geometry_id) " +
                "values (" + String.join(",", Collections.nCopies(7, "?")) + ")";
    }

    public long doImport(ImplicitGeometry implicitGeometry, long featureId) throws ImportException, SQLException {
        String objectId = implicitGeometry.getObjectId().orElse(null);

        long implicitGeometryId = lookupImplicitGeometry(objectId);
        if (implicitGeometryId > 0) {
            cacheTarget(CacheType.IMPLICIT_GEOMETRY, objectId, implicitGeometryId);
            return implicitGeometryId;
        } else {
            implicitGeometryId = nextSequenceValue(Sequence.IMPLICIT_GEOMETRY);
        }

        stmt.setLong(1, implicitGeometryId);
        stmt.setString(2, implicitGeometry.getOrCreateObjectId());

        if (implicitGeometry.getGeometry().isPresent()) {
            stmt.setNull(3, Types.VARCHAR);
            stmt.setNull(4, Types.VARCHAR);
            stmt.setNull(5, Types.VARCHAR);
            stmt.setNull(6, Types.OTHER);
            stmt.setLong(7, tableHelper.getOrCreateImporter(GeometryImporter.class)
                    .doImport(implicitGeometry.getGeometry().get(), true, featureId)
                    .getId());
        } else if (implicitGeometry.getLibraryObject().isPresent()) {
            ExternalFile libraryObject = implicitGeometry.getLibraryObject().get();
            FileLocator locator = getFileLocator(libraryObject);

            stmt.setString(3, libraryObject.getMimeType().orElse(null));
            stmt.setString(4, libraryObject.getMimeTypeCodeSpace().orElse(null));
            stmt.setString(5, locator.getFileName());

            try (InputStream stream = locator.openStream()) {
                stmt.setBytes(6, stream.readAllBytes());
            } catch (IOException e) {
                throw new ImportException("Failed to load library object " + libraryObject.getFileLocation() + ".", e);
            }

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

    private long lookupImplicitGeometry(String objectId) throws SQLException {
        if (objectId != null) {
            lookupImplicitGeometry.setString(1, objectId);
            try (ResultSet rs = lookupImplicitGeometry.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }

        return 0;
    }

    @Override
    public void close() throws SQLException {
        super.close();
        lookupImplicitGeometry.close();
    }
}
