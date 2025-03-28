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

package org.citydb.operation.exporter.hierarchy;

import org.citydb.model.feature.Feature;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citydb.model.property.Property;
import org.citydb.model.property.PropertyDescriptor;
import org.citydb.operation.exporter.ExportException;
import org.citydb.operation.exporter.ExportHelper;
import org.citydb.operation.exporter.address.AddressExporter;
import org.citydb.operation.exporter.appearance.AppearanceExporter;
import org.citydb.operation.exporter.feature.FeatureExporter;
import org.citydb.operation.exporter.geometry.GeometryExporter;
import org.citydb.operation.exporter.geometry.ImplicitGeometryExporter;
import org.citydb.operation.exporter.options.AppearanceOptions;
import org.citydb.operation.exporter.property.PropertyExporter;
import org.citydb.operation.exporter.property.PropertyStub;
import org.citydb.operation.exporter.util.LodFilter;
import org.citydb.operation.exporter.util.TableHelper;
import org.citydb.operation.exporter.util.ValidityFilter;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class HierarchyBuilder {
    private final long rootId;
    private final ExportHelper helper;
    private final ValidityFilter validityFilter;
    private final LodFilter lodFilter;
    private final TableHelper tableHelper;
    private final PropertyBuilder propertyBuilder;
    private final Hierarchy hierarchy = new Hierarchy();
    private final Map<Long, List<PropertyStub>> propertyStubs = new HashMap<>();
    private final boolean exportAppearances;

    private HierarchyBuilder(long rootId, ExportHelper helper) {
        this.rootId = rootId;
        this.helper = helper;
        validityFilter = helper.getValidityFilter();
        lodFilter = helper.getLodFilter();
        tableHelper = helper.getTableHelper();
        propertyBuilder = new PropertyBuilder(helper);
        exportAppearances = helper.getOptions().getAppearanceOptions()
                .orElseGet(AppearanceOptions::new)
                .isExportAppearances();
    }

    public static HierarchyBuilder newInstance(long rootId, ExportHelper helper) {
        return new HierarchyBuilder(rootId, helper);
    }

    public HierarchyBuilder initialize(ResultSet rs) throws ExportException, SQLException {
        Set<Long> featureIds = new HashSet<>();
        Set<Long> geometryIds = new HashSet<>();
        Set<Long> appearanceIds = new HashSet<>();
        Set<Long> addressIds = new HashSet<>();
        Set<Long> implicitGeometryIds = new HashSet<>();
        Map<Long, Integer> referees = new HashMap<>();

        while (rs.next()) {
            long featureId = rs.getLong("val_feature_id");
            if (!rs.wasNull() && hierarchy.getFeature(featureId) == null) {
                featureIds.add(featureId);
            }

            long geometryId = rs.getLong("val_geometry_id");
            if (!rs.wasNull()
                    && hierarchy.getGeometry(geometryId) == null
                    && lodFilter.filter(rs.getString("val_lod"))) {
                geometryIds.add(geometryId);
            }

            if (exportAppearances) {
                long appearanceId = rs.getLong("val_appearance_id");
                if (!rs.wasNull()) {
                    appearanceIds.add(appearanceId);
                }
            }

            long addressId = rs.getLong("val_address_id");
            if (!rs.wasNull()) {
                addressIds.add(addressId);
            }

            long implicitGeometryId = rs.getLong("val_implicitgeom_id");
            if (!rs.wasNull() && lodFilter.filter(rs.getString("val_lod"))) {
                implicitGeometryIds.add(implicitGeometryId);
            }

            long parentFeatureId = rs.getLong("feature_id");
            if (!rs.wasNull()) {
                PropertyStub propertyStub = tableHelper.getOrCreateExporter(PropertyExporter.class)
                        .doExport(parentFeatureId, rs);
                if (propertyStub != null) {
                    propertyStubs.computeIfAbsent(parentFeatureId, v -> new ArrayList<>()).add(propertyStub);
                    if (propertyStub.getDataType() == DataType.FEATURE_PROPERTY) {
                        referees.merge(featureId, 1, Integer::sum);
                    }
                }
            }
        }

        if (!featureIds.isEmpty()) {
            Set<Long> removedFeatureIds = new HashSet<>();
            for (Map.Entry<Long, Feature> entry : tableHelper.getOrCreateExporter(FeatureExporter.class)
                    .doExport(featureIds).entrySet()) {
                long featureId = entry.getKey();
                Feature feature = entry.getValue();
                if (!removedFeatureIds.contains(featureId)) {
                    if (featureId != rootId && !validityFilter.filter(feature)) {
                        removeFeature(featureId, removedFeatureIds, referees);
                    } else {
                        hierarchy.addFeature(featureId, feature);
                    }
                }
            }

            if (!removedFeatureIds.isEmpty()) {
                hierarchy.getFeatures().keySet().removeAll(removedFeatureIds);
            }
        }

        if (!geometryIds.isEmpty()) {
            tableHelper.getOrCreateExporter(GeometryExporter.class)
                    .doExport(geometryIds, false)
                    .forEach(hierarchy::addGeometry);
        }

        if (exportAppearances) {
            tableHelper.getOrCreateExporter(AppearanceExporter.class)
                    .doExport(appearanceIds, implicitGeometryIds)
                    .forEach(hierarchy::addAppearance);
        }

        if (!addressIds.isEmpty()) {
            tableHelper.getOrCreateExporter(AddressExporter.class)
                    .doExport(addressIds)
                    .forEach(hierarchy::addAddress);
        }

        if (!implicitGeometryIds.isEmpty()) {
            tableHelper.getOrCreateExporter(ImplicitGeometryExporter.class)
                    .doExport(implicitGeometryIds, hierarchy.getAppearances().values())
                    .forEach(hierarchy::addImplicitGeometry);
        }

        return this;
    }

    public Hierarchy build() {
        Feature root = hierarchy.getFeature(rootId);
        if (root != null) {
            helper.lookupAndPut(root);

            for (List<PropertyStub> propertyStubs : this.propertyStubs.values()) {
                Iterator<PropertyStub> iterator = propertyStubs.iterator();
                while (iterator.hasNext()) {
                    PropertyStub propertyStub = iterator.next();
                    hierarchy.addProperty(propertyStub.getDescriptor().getId(),
                            propertyBuilder.build(propertyStub, hierarchy));
                    iterator.remove();
                }
            }

            for (Property<?> property : hierarchy.getProperties().values()) {
                long parentId = property.getDescriptor().map(PropertyDescriptor::getParentId).orElse(0L);
                if (parentId != 0) {
                    Attribute attribute = hierarchy.getProperty(parentId, Attribute.class);
                    if (attribute != null) {
                        attribute.addProperty(property);
                    }
                } else {
                    long featureId = property.getDescriptor().map(PropertyDescriptor::getFeatureId).orElse(0L);
                    Feature feature = hierarchy.getFeature(featureId);
                    if (feature != null) {
                        feature.addProperty(property);
                    }
                }
            }
        }

        return hierarchy;
    }

    private void removeFeature(long featureId, Set<Long> removedFeatureIds, Map<Long, Integer> referees) {
        removedFeatureIds.add(featureId);
        List<PropertyStub> propertyStubs = this.propertyStubs.remove(featureId);
        if (propertyStubs != null) {
            for (PropertyStub propertyStub : propertyStubs) {
                if (propertyStub.getDataType() == DataType.FEATURE_PROPERTY) {
                    long nestedFeatureId = propertyStub.getFeatureId();
                    if (referees.merge(nestedFeatureId, -1, Integer::sum) == 0) {
                        removeFeature(nestedFeatureId, removedFeatureIds, referees);
                    }
                }
            }
        }
    }
}
