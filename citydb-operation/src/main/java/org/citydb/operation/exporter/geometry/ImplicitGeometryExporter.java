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

package org.citydb.operation.exporter.geometry;

import org.citydb.database.schema.Table;
import org.citydb.model.appearance.Appearance;
import org.citydb.model.appearance.AppearanceDescriptor;
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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

public class ImplicitGeometryExporter extends DatabaseExporter {
    private final BlobExporter blobExporter;
    private final ExternalFileHelper externalFileHelper;

    public ImplicitGeometryExporter(ExportHelper helper) throws SQLException {
        super(helper);
        blobExporter = new BlobExporter(Table.IMPLICIT_GEOMETRY, "id", "library_object", helper);
        externalFileHelper = ExternalFileHelper.newInstance(helper)
                .withRelativeOutputFolder(ExportConstants.LIBRARY_OBJECTS_DIR)
                .withFileNamePrefix(ExportConstants.LIBRARY_OBJECTS_PREFIX)
                .createUniqueFileNames(true);
        stmt = helper.getConnection().prepareStatement(getBaseQuery() +
                "where ig.id = ?");
    }

    private String getBaseQuery() {
        return "select ig.id, ig.mime_type, ig.mime_type_codespace, ig.reference_to_library, " +
                "ig.relative_geometry_id, gd.implicit_geometry, " +
                "gd.geometry_properties, gd.feature_id as geometry_feature_id " +
                "from " + tableHelper.getPrefixedTableName(Table.IMPLICIT_GEOMETRY) + " ig " +
                "left join " + tableHelper.getPrefixedTableName(Table.GEOMETRY_DATA) + " gd on ig.relative_geometry_id = gd.id ";
    }

    private String getQuery(Set<Long> ids) {
        return getBaseQuery() +
                "where " + adapter.getSchemaAdapter().getInOperator("ig.id", ids);
    }

    public ImplicitGeometry doExport(long id) throws ExportException, SQLException {
        Collection<Appearance> appearances = tableHelper.getOrCreateExporter(AppearanceExporter.class)
                .doExport(Collections.emptySet(), Collections.singleton(id))
                .values();

        stmt.setLong(1, id);
        try (ResultSet rs = stmt.executeQuery()) {
            return doExport(appearances, rs).get(id);
        }
    }

    public Map<Long, ImplicitGeometry> doExport(Set<Long> ids, Collection<Appearance> appearances) throws ExportException, SQLException {
        if (!ids.isEmpty()) {
            try (Statement stmt = helper.getConnection().createStatement();
                 ResultSet rs = stmt.executeQuery(getQuery(ids))) {
                return doExport(appearances, rs);
            }
        } else {
            return Collections.emptyMap();
        }
    }

    private Map<Long, ImplicitGeometry> doExport(Collection<Appearance> appearances, ResultSet rs) throws ExportException, SQLException {
        Map<Long, ImplicitGeometry> implicitGeometries = new HashMap<>();
        Map<Long, List<Appearance>> appearancesById = appearances.stream()
                .filter(appearance -> appearance.getDescriptor()
                        .map(AppearanceDescriptor::getImplicitGeometryId).isPresent())
                .collect(Collectors.groupingBy(appearance -> appearance.getDescriptor()
                        .map(AppearanceDescriptor::getImplicitGeometryId).orElse(0L)));

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
                implicitGeometries.put(id, implicitGeometry);
                for (Appearance appearance : appearancesById.getOrDefault(id, Collections.emptyList())) {
                    implicitGeometry.addAppearance(
                            AppearanceProperty.of(Name.of("appearance", Namespaces.APPEARANCE), appearance));
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
