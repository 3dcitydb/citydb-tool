/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.geometry;

import org.citydb.model.appearance.Appearance;
import org.citydb.model.common.ExternalFile;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.property.AppearanceProperty;
import org.citydb.operation.exporter.ExportConstants;
import org.citydb.operation.exporter.ExportException;
import org.citydb.operation.exporter.ExportHelper;
import org.citydb.operation.exporter.appearance.AppearanceExporter;
import org.citydb.operation.exporter.common.BlobExporter;
import org.citydb.operation.exporter.common.DatabaseExporter;
import org.citydb.operation.exporter.util.ExternalFileHelper;
import org.citydb.sqlbuilder.literal.Placeholder;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Table;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class ImplicitGeometryExporter extends DatabaseExporter {
    private final BlobExporter blobExporter;
    private final ExternalFileHelper externalFileHelper;

    public ImplicitGeometryExporter(ExportHelper helper) throws SQLException {
        super(helper);
        Table implicitGeometry = tableHelper.getTable(org.citydb.database.schema.Table.IMPLICIT_GEOMETRY);
        blobExporter = new BlobExporter(implicitGeometry, "id", "library_object", helper);
        externalFileHelper = ExternalFileHelper.newInstance(helper)
                .withRelativeOutputFolder(ExportConstants.LIBRARY_OBJECTS_DIR)
                .withFileNamePrefix(ExportConstants.LIBRARY_OBJECTS_PREFIX)
                .createUniqueFileNames(true);
        stmt = helper.getConnection().prepareStatement(getQuery(implicitGeometry).toSql());
    }

    private Select getQuery(Table implicitGeometry) {
        Table geometryData = tableHelper.getTable(org.citydb.database.schema.Table.GEOMETRY_DATA);
        return Select.newInstance()
                .select(implicitGeometry.columns("id", "objectid", "mime_type", "mime_type_codespace",
                        "reference_to_library", "relative_geometry_id"))
                .select(geometryData.columns("implicit_geometry", "geometry_properties", "feature_id"))
                .from(implicitGeometry)
                .leftJoin(geometryData).on(geometryData.column("id")
                        .eq(implicitGeometry.column("relative_geometry_id")))
                .where(operationHelper.inArray(implicitGeometry.column("id"), Placeholder.empty()));
    }

    public ImplicitGeometry doExport(long id) throws ExportException, SQLException {
        Collection<Appearance> appearances = tableHelper.getOrCreateExporter(AppearanceExporter.class)
                .doExport(Collections.emptySet(), Collections.singleton(id))
                .values();

        setLongArrayOrNull(1, List.of(id));
        try (ResultSet rs = stmt.executeQuery()) {
            return doExport(Map.of(id, appearances), rs).get(id);
        }
    }

    public Map<Long, ImplicitGeometry> doExport(Set<Long> ids, Map<Long, ? extends Collection<Appearance>> appearances) throws ExportException, SQLException {
        if (!ids.isEmpty()) {
            setLongArrayOrNull(1, ids);
            try (ResultSet rs = stmt.executeQuery()) {
                return doExport(appearances, rs);
            }
        } else {
            return Collections.emptyMap();
        }
    }

    private Map<Long, ImplicitGeometry> doExport(Map<Long, ? extends Collection<Appearance>> appearancesById, ResultSet rs) throws ExportException, SQLException {
        Map<Long, ImplicitGeometry> implicitGeometries = new HashMap<>();
        while (rs.next()) {
            ImplicitGeometry implicitGeometry = null;
            long id = rs.getLong("id");

            long geometryId = rs.getLong("relative_geometry_id");
            if (!rs.wasNull()) {
                Geometry<?> geometry = tableHelper.getOrCreateExporter(GeometryExporter.class)
                        .doExport(geometryId, true, rs);
                if (geometry != null) {
                    implicitGeometry = ImplicitGeometry.of(geometry);
                }
            } else {
                String uri = rs.getString("reference_to_library");
                if (uri != null) {
                    String mimeType = rs.getString("mime_type");
                    ExternalFile libraryObject = externalFileHelper.createExternalFile(id, uri, mimeType);
                    if (libraryObject != null) {
                        blobExporter.addBatch(id, libraryObject);
                        implicitGeometry = ImplicitGeometry.of(libraryObject
                                .setMimeType(mimeType)
                                .setMimeTypeCodeSpace(rs.getString("mime_type_codespace")));
                    }
                }
            }

            if (implicitGeometry != null) {
                String objectId = rs.getString("objectid");
                if (!rs.wasNull()) {
                    implicitGeometry.setObjectId(objectId);
                }

                implicitGeometries.put(id, implicitGeometry);
                Collection<Appearance> appearances = appearancesById.get(id);
                if (appearances != null) {
                    for (Appearance appearance : appearances) {
                        implicitGeometry.addAppearance(
                                AppearanceProperty.of(Name.of("appearance", Namespaces.APPEARANCE), appearance));
                    }
                }
            }
        }

        return implicitGeometries;
    }

    @Override
    public void close() throws ExportException, SQLException {
        super.close();
        blobExporter.close();
    }
}
