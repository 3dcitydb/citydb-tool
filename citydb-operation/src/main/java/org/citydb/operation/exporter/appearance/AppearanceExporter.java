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

package org.citydb.operation.exporter.appearance;

import org.citydb.database.schema.ObjectClass;
import org.citydb.database.schema.Table;
import org.citydb.model.appearance.*;
import org.citydb.model.common.Reference;
import org.citydb.model.common.ReferenceType;
import org.citydb.operation.exporter.ExportException;
import org.citydb.operation.exporter.ExportHelper;
import org.citydb.operation.exporter.common.DatabaseExporter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.*;

public class AppearanceExporter extends DatabaseExporter {
    private final ObjectClass material;
    private final ObjectClass parameterizedTexture;
    private final ObjectClass georeferencedTexture;

    public AppearanceExporter(ExportHelper helper) throws SQLException {
        super(helper);
        material = objectClassHelper.getObjectClass(X3DMaterial.newInstance().getName());
        parameterizedTexture = objectClassHelper.getObjectClass(ParameterizedTexture.newInstance().getName());
        georeferencedTexture = objectClassHelper.getObjectClass(GeoreferencedTexture.newInstance().getName());
        stmt = helper.getConnection().prepareStatement(getBaseQuery() +
                "where a.id = ?");
    }

    private String getBaseQuery() {
        return "select a.id, a.objectid, a.identifier, a.identifier_codespace, a.theme, a.creation_date, " +
                "a.termination_date, a.valid_from, a.valid_to, a.feature_id, a.implicit_geometry_id, " +
                "sd.id as sd_id, sd.objectid as sd_objectid, sd.identifier as sd_identifier, " +
                "sd.identifier_codespace as sd_identifier_codespace, sd.is_front, sd.objectclass_id, " +
                "sd.x3d_shininess, sd.x3d_transparency, sd.x3d_ambient_intensity, sd.x3d_specular_color, " +
                "sd.x3d_diffuse_color, sd.x3d_emissive_color, sd.x3d_is_smooth, sd.tex_image_id, " +
                "sd.tex_texture_type, sd.tex_wrap_mode, sd.tex_border_color, sd.gt_orientation, " +
                "sd.gt_reference_point, ti.image_uri, ti.mime_type, ti.mime_type_codespace, " +
                "sdm.geometry_data_id, sdm.material_mapping, sdm.texture_mapping, sdm.world_to_texture_mapping, " +
                "sdm.georeferenced_texture_mapping " +
                "from " + tableHelper.getPrefixedTableName(Table.APPEARANCE) + " a " +
                "inner join " + tableHelper.getPrefixedTableName(Table.APPEAR_TO_SURFACE_DATA) + " a2sd on a.id = a2sd.appearance_id " +
                "inner join " + tableHelper.getPrefixedTableName(Table.SURFACE_DATA) + " sd on a2sd.surface_data_id = sd.id " +
                "left join " + tableHelper.getPrefixedTableName(Table.TEX_IMAGE) + " ti on sd.tex_image_id = ti.id " +
                "left join " + tableHelper.getPrefixedTableName(Table.SURFACE_DATA_MAPPING) + " sdm on sd.id = sdm.surface_data_id ";
    }

    private String getQuery(Set<Long> ids, Set<Long> implicitGeometryIds) {
        return getBaseQuery() +
                "where " + adapter.getSchemaAdapter().getInOperator("a.id", ids) +
                " or " + adapter.getSchemaAdapter().getInOperator("a.implicit_geometry_id", implicitGeometryIds);
    }

    public Appearance doExport(long id) throws ExportException, SQLException {
        stmt.setLong(1, id);
        try (ResultSet rs = stmt.executeQuery()) {
            return doExport(rs).get(id);
        }
    }

    public Map<Long, Appearance> doExport(Set<Long> ids, Set<Long> implicitGeometryIds) throws ExportException, SQLException {
        if (!ids.isEmpty() || !implicitGeometryIds.isEmpty()) {
            try (Statement stmt = helper.getConnection().createStatement();
                 ResultSet rs = stmt.executeQuery(getQuery(ids, implicitGeometryIds))) {
                return doExport(rs);
            }
        } else {
            return Collections.emptyMap();
        }
    }

    private Map<Long, Appearance> doExport(ResultSet rs) throws ExportException, SQLException {
        Map<Long, Appearance> appearances = new HashMap<>();
        Map<Long, SurfaceData<?>> surfaceDataObjects = new HashMap<>();
        Map<Long, Set<Long>> surfaceDataByAppearance = new HashMap<>();

        while (rs.next()) {
            long id = rs.getLong("id");

            Appearance appearance = appearances.get(id);
            if (appearance == null) {
                appearance = Appearance.newInstance()
                        .setTheme(rs.getString("theme"))
                        .setObjectId(rs.getString("objectid"))
                        .setIdentifier(rs.getString("identifier"))
                        .setIdentifierCodeSpace(rs.getString("identifier_codespace"))
                        .setCreationDate(rs.getObject("creation_date", OffsetDateTime.class))
                        .setTerminationDate(rs.getObject("termination_date", OffsetDateTime.class))
                        .setValidFrom(rs.getObject("valid_from", OffsetDateTime.class))
                        .setValidTo(rs.getObject("valid_to", OffsetDateTime.class))
                        .setDescriptor(AppearanceDescriptor.of(id)
                                .setFeatureId(rs.getLong("feature_id"))
                                .setImplicitGeometryId(rs.getLong("implicit_geometry_id")));
                appearances.put(id, appearance);
            }

            long surfaceDataId = rs.getLong("sd_id");
            SurfaceData<?> surfaceData = surfaceDataObjects.get(surfaceDataId);
            if (surfaceData == null) {
                ObjectClass objectClass = objectClassHelper.getObjectClass(rs.getInt("objectclass_id"));
                if (objectClass == material) {
                    surfaceData = tableHelper.getOrCreateExporter(X3DMaterialExporter.class).doExport(rs);
                } else if (objectClass == parameterizedTexture) {
                    surfaceData = tableHelper.getOrCreateExporter(ParameterizedTextureExporter.class).doExport(rs);
                } else if (objectClass == georeferencedTexture) {
                    surfaceData = tableHelper.getOrCreateExporter(GeoreferencedTextureExporter.class).doExport(rs);
                }

                surfaceDataObjects.put(surfaceDataId, surfaceData);
            }

            if (surfaceData != null) {
                if (surfaceDataByAppearance.computeIfAbsent(id, v -> new HashSet<>()).add(surfaceDataId)) {
                    appearance.getSurfaceData().add(helper.lookupAndPut(surfaceData) ?
                            SurfaceDataProperty.of(Reference.of(
                                    surfaceData.getOrCreateObjectId(),
                                    ReferenceType.LOCAL_REFERENCE)) :
                            SurfaceDataProperty.of(surfaceData));
                }

                tableHelper.getOrCreateExporter(SurfaceDataMappingExporter.class).doExport(surfaceData, rs);
            }
        }

        return appearances;
    }
}
