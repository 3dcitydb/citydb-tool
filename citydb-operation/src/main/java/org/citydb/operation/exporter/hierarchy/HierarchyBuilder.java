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

package org.citydb.operation.exporter.hierarchy;

import org.citydb.model.feature.Feature;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.Property;
import org.citydb.model.property.PropertyDescriptor;
import org.citydb.operation.exporter.ExportException;
import org.citydb.operation.exporter.ExportHelper;
import org.citydb.operation.exporter.address.AddressExporter;
import org.citydb.operation.exporter.appearance.AppearanceExporter;
import org.citydb.operation.exporter.feature.FeatureExporter;
import org.citydb.operation.exporter.geometry.GeometryExporter;
import org.citydb.operation.exporter.geometry.ImplicitGeometryExporter;
import org.citydb.operation.exporter.property.PropertyExporter;
import org.citydb.operation.exporter.property.PropertyStub;
import org.citydb.operation.exporter.util.TableHelper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class HierarchyBuilder {
    private final TableHelper tableHelper;
    private final PropertyBuilder propertyBuilder;
    private final Hierarchy hierarchy = new Hierarchy();
    private final List<PropertyStub> propertyStubs = new ArrayList<>();

    private HierarchyBuilder(ExportHelper helper) {
        tableHelper = helper.getTableHelper();
        propertyBuilder = new PropertyBuilder(helper);
    }

    public static HierarchyBuilder newInstance(ExportHelper helper) {
        return new HierarchyBuilder(helper);
    }

    public HierarchyBuilder initialize(ResultSet rs) throws ExportException, SQLException {
        return initialize(rs, Collections.emptySet());
    }

    public HierarchyBuilder initialize(ResultSet rs, Set<Long> exportedFeatures) throws ExportException, SQLException {
        Set<Long> appearanceIds = new HashSet<>();
        Set<Long> implicitGeometryIds = new HashSet<>();

        if (exportedFeatures != null) {
            hierarchy.getInlineFeatures().addAll(exportedFeatures);
        }

        while (rs.next()) {
            long nestedFeatureId = rs.getLong("val_feature_id");
            if (!rs.wasNull()) {
                Feature feature = hierarchy.getFeature(nestedFeatureId);
                if (feature == null) {
                    hierarchy.addFeature(nestedFeatureId, tableHelper.getOrCreateExporter(FeatureExporter.class)
                            .doExport(nestedFeatureId, rs));
                }

                int referenceType = rs.getInt("val_reference_type");
                if (referenceType == 0) {
                    hierarchy.addInlineFeature(nestedFeatureId);
                }
            }

            long geometryId = rs.getLong("val_geometry_id");
            if (!rs.wasNull() && hierarchy.getGeometry(geometryId) == null) {
                hierarchy.addGeometry(geometryId, tableHelper.getOrCreateExporter(GeometryExporter.class)
                        .doExport(geometryId, false, rs));
            }

            long appearanceId = rs.getLong("val_appearance_id");
            if (!rs.wasNull()) {
                appearanceIds.add(appearanceId);
            }

            long addressId = rs.getLong("val_address_id");
            if (!rs.wasNull() && hierarchy.getAddress(addressId) == null) {
                hierarchy.addAddress(addressId, tableHelper.getOrCreateExporter(AddressExporter.class)
                        .doExport(addressId, rs));
            }

            long implicitGeometryId = rs.getLong("val_implicitgeom_id");
            if (!rs.wasNull()) {
                implicitGeometryIds.add(implicitGeometryId);
            }

            long featureId = rs.getLong("feature_id");
            if (!rs.wasNull()) {
                PropertyStub propertyStub = tableHelper.getOrCreateExporter(PropertyExporter.class)
                        .doExport(featureId, rs);
                if (propertyStub != null) {
                    propertyStubs.add(propertyStub);
                }
            }
        }

        tableHelper.getOrCreateExporter(AppearanceExporter.class)
                .doExport(appearanceIds, implicitGeometryIds)
                .forEach(hierarchy::addAppearance);

        tableHelper.getOrCreateExporter(ImplicitGeometryExporter.class)
                .doExport(implicitGeometryIds, hierarchy.getAppearances().values())
                .forEach(hierarchy::addImplicitGeometry);

        return this;
    }

    public Hierarchy build() throws ExportException, SQLException {
        Iterator<PropertyStub> iterator = propertyStubs.iterator();
        while (iterator.hasNext()) {
            PropertyStub propertyStub = iterator.next();
            hierarchy.addProperty(propertyStub.getDescriptor().getId(),
                    propertyBuilder.build(propertyStub, hierarchy));
            iterator.remove();
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

        return hierarchy;
    }
}
