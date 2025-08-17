/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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

package org.citydb.operation.exporter.appearance;

import org.citydb.database.schema.FeatureType;
import org.citydb.model.appearance.*;
import org.citydb.model.common.Reference;
import org.citydb.operation.exporter.ExportException;
import org.citydb.operation.exporter.ExportHelper;
import org.citydb.operation.exporter.common.DatabaseExporter;
import org.citydb.operation.exporter.options.AppearanceOptions;
import org.citydb.sqlbuilder.literal.Placeholder;
import org.citydb.sqlbuilder.operation.BooleanExpression;
import org.citydb.sqlbuilder.operation.Operators;
import org.citydb.sqlbuilder.query.Select;
import org.citydb.sqlbuilder.schema.Table;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

public class AppearanceExporter extends DatabaseExporter {
    private final Table appearance;
    private final Select select;
    private final FeatureType material;
    private final FeatureType parameterizedTexture;
    private final FeatureType georeferencedTexture;

    public AppearanceExporter(ExportHelper helper) throws SQLException {
        super(helper);
        appearance = tableHelper.getTable(org.citydb.database.schema.Table.APPEARANCE);
        select = getBaseQuery();
        material = schemaMapping.getFeatureType(X3DMaterial.newInstance().getName());
        parameterizedTexture = schemaMapping.getFeatureType(ParameterizedTexture.newInstance().getName());
        georeferencedTexture = schemaMapping.getFeatureType(GeoreferencedTexture.newInstance().getName());
        stmt = helper.getConnection().prepareStatement(Select.of(select)
                .where(appearance.column("id").eq(Placeholder.empty()))
                .toSql());
    }

    private Select getBaseQuery() {
        Table appearToSurfaceData = tableHelper.getTable(org.citydb.database.schema.Table.APPEAR_TO_SURFACE_DATA);
        Table surfaceData = tableHelper.getTable(org.citydb.database.schema.Table.SURFACE_DATA);
        Table texImage = tableHelper.getTable(org.citydb.database.schema.Table.TEX_IMAGE);
        Table surfaceDataMapping = tableHelper.getTable(org.citydb.database.schema.Table.SURFACE_DATA_MAPPING);
        BooleanExpression themeFilter = getThemeFilter();

        Select select = Select.newInstance()
                .select(appearance.columns("id", "objectid", "identifier", "identifier_codespace", "theme",
                        "feature_id", "implicit_geometry_id"))
                .select(surfaceData.columns(Map.of("id", "sd_id", "objectid", "sd_objectid", "identifier",
                        "sd_identifier", "identifier_codespace", "sd_identifier_codespace")))
                .select(surfaceData.columns("is_front", "objectclass_id", "x3d_shininess", "x3d_transparency",
                        "x3d_ambient_intensity", "x3d_specular_color", "x3d_diffuse_color", "x3d_emissive_color",
                        "x3d_is_smooth", "tex_image_id", "tex_texture_type", "tex_wrap_mode", "tex_border_color",
                        "gt_orientation"))
                .select(helper.getTransformOperator(surfaceData.column("gt_reference_point")))
                .select(texImage.columns("image_uri", "mime_type", "mime_type_codespace"))
                .select(surfaceDataMapping.columns("geometry_data_id", "material_mapping", "texture_mapping",
                        "world_to_texture_mapping", "georeferenced_texture_mapping"))
                .from(appearance)
                .join(appearToSurfaceData).on(appearToSurfaceData.column("appearance_id").eq(appearance.column("id")))
                .join(surfaceData).on(surfaceData.column("id").eq(appearToSurfaceData.column("surface_data_id")))
                .leftJoin(texImage).on(texImage.column("id").eq(surfaceData.column("tex_image_id")))
                .leftJoin(surfaceDataMapping).on(surfaceDataMapping.column("surface_data_id")
                        .eq(surfaceData.column("id")));

        return themeFilter != null ?
                select.where(themeFilter) :
                select;
    }

    private Select getQuery(Set<Long> ids, Set<Long> implicitGeometryIds) {
        BooleanExpression condition;
        if (!ids.isEmpty() && !implicitGeometryIds.isEmpty()) {
            condition = Operators.or(operationHelper.in(appearance.column("id"), ids),
                    operationHelper.in(appearance.column("implicit_geometry_id"), implicitGeometryIds));
        } else if (!implicitGeometryIds.isEmpty()) {
            condition = operationHelper.in(appearance.column("implicit_geometry_id"), implicitGeometryIds);
        } else {
            condition = operationHelper.in(appearance.column("id"), ids);
        }

        return Select.of(select).where(condition);
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
                 ResultSet rs = stmt.executeQuery(getQuery(ids, implicitGeometryIds).toSql())) {
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
                        .setDescriptor(AppearanceDescriptor.of(id)
                                .setFeatureId(rs.getLong("feature_id"))
                                .setImplicitGeometryId(rs.getLong("implicit_geometry_id")));
                appearances.put(id, appearance);
            }

            long surfaceDataId = rs.getLong("sd_id");
            SurfaceData<?> surfaceData = surfaceDataObjects.get(surfaceDataId);
            if (surfaceData == null) {
                FeatureType featureType = schemaMapping.getFeatureType(rs.getInt("objectclass_id"));
                if (featureType == material) {
                    surfaceData = tableHelper.getOrCreateExporter(X3DMaterialExporter.class).doExport(rs);
                } else if (featureType == parameterizedTexture) {
                    surfaceData = tableHelper.getOrCreateExporter(ParameterizedTextureExporter.class).doExport(rs);
                } else if (featureType == georeferencedTexture) {
                    surfaceData = tableHelper.getOrCreateExporter(GeoreferencedTextureExporter.class).doExport(rs);
                }

                surfaceDataObjects.put(surfaceDataId, surfaceData);
            }

            if (surfaceData != null) {
                if (surfaceDataByAppearance.computeIfAbsent(id, v -> new HashSet<>()).add(surfaceDataId)) {
                    appearance.getSurfaceData().add(helper.lookupAndPut(surfaceData) ?
                            SurfaceDataProperty.of(Reference.of(surfaceData.getOrCreateObjectId())) :
                            SurfaceDataProperty.of(surfaceData));
                }

                tableHelper.getOrCreateExporter(SurfaceDataMappingExporter.class).doExport(surfaceData, rs);
            }
        }

        return appearances;
    }

    private BooleanExpression getThemeFilter() {
        AppearanceOptions options = helper.getOptions().getAppearanceOptions().orElse(null);
        if (options != null && options.hasThemes()) {
            Set<String> themes = options.getThemes();
            boolean containsNullTheme = themes.contains(null);
            if (containsNullTheme && themes.size() == 1) {
                return appearance.column("theme").isNull();
            } else {
                BooleanExpression filter = operationHelper.in(appearance.column("theme"), themes.stream()
                        .filter(Objects::nonNull)
                        .toList());
                return containsNullTheme ?
                        Operators.or(appearance.column("theme").isNull(), filter) :
                        filter;
            }
        } else {
            return null;
        }
    }
}
