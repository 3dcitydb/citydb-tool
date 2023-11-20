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

package org.citydb.io.citygml.adapter.core;

import org.citydb.io.citygml.adapter.geometry.builder.Lod;
import org.citydb.io.citygml.adapter.geometry.serializer.MultiCurvePropertyAdapter;
import org.citydb.io.citygml.adapter.geometry.serializer.MultiSurfacePropertyAdapter;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.FeatureProperty;
import org.citydb.model.property.GeometryProperty;
import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.core.AbstractThematicSurface;
import org.citygml4j.core.model.core.QualifiedAreaProperty;
import org.citygml4j.core.model.deprecated.core.DeprecatedPropertiesOfAbstractThematicSurface;

public abstract class AbstractThematicSurfaceAdapter<T extends AbstractThematicSurface> extends AbstractSpaceBoundaryAdapter<T> {

    @Override
    public void build(T source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.isSetAreas()) {
            for (QualifiedAreaProperty property : source.getAreas()) {
                if (property != null) {
                    helper.addAttribute(Name.of("area", Namespaces.CORE), property.getObject(), target,
                            QualifiedAreaAdapter.class);
                }
            }
        }

        if (source.getLod0MultiCurve() != null) {
            helper.addCurveGeometry(Name.of("lod0MultiCurve", Namespaces.CORE), source.getLod0MultiCurve(),
                    Lod.of(0), target);
        }

        if (source.getLod0MultiSurface() != null) {
            helper.addSurfaceGeometry(Name.of("lod0MultiSurface", Namespaces.CORE), source.getLod0MultiSurface(),
                    Lod.of(0), target);
        }

        if (source.getLod1MultiSurface() != null) {
            helper.addSurfaceGeometry(Name.of("lod1MultiSurface", Namespaces.CORE), source.getLod1MultiSurface(),
                    Lod.of(1), target);
        }

        if (source.getLod2MultiSurface() != null) {
            helper.addSurfaceGeometry(Name.of("lod2MultiSurface", Namespaces.CORE), source.getLod2MultiSurface(),
                    Lod.of(2), target);
        }

        if (source.getLod3MultiSurface() != null) {
            helper.addSurfaceGeometry(Name.of("lod3MultiSurface", Namespaces.CORE), source.getLod3MultiSurface(),
                    Lod.of(3), target);
        }

        if (source.getPointCloud() != null) {
            helper.addFeature(Name.of("pointCloud", Namespaces.CORE), source.getPointCloud(), target);
        }

        if (source.hasDeprecatedProperties()) {
            DeprecatedPropertiesOfAbstractThematicSurface properties = source.getDeprecatedProperties();
            if (properties.getLod4MultiSurface() != null) {
                helper.addSurfaceGeometry(Name.of("lod4MultiSurface", Namespaces.DEPRECATED),
                        properties.getLod4MultiSurface(), Lod.of(4), target);
            }
        }
    }

    @Override
    public void serialize(Feature source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        CityGMLVersion version = helper.getCityGMLVersion();
        boolean isCityGML3 = version == CityGMLVersion.v3_0;
        boolean useLod4asLod3 = helper.isUseLod4AsLod3();

        for (Attribute attribute : source.getAttributes().get(Name.of("area", Namespaces.CORE))) {
            target.getAreas().add(new QualifiedAreaProperty(
                    helper.getAttribute(attribute, QualifiedAreaAdapter.class)));
        }

        if (isCityGML3 || geometrySupport.supportsLod0MultiCurve(version, target)) {
            GeometryProperty lod0MultiCurve = source.getGeometries()
                    .getFirst(Name.of("lod0MultiCurve", Namespaces.CORE))
                    .orElse(null);
            if (lod0MultiCurve != null) {
                target.setLod0MultiCurve(helper.getGeometryProperty(lod0MultiCurve, MultiCurvePropertyAdapter.class));
            }
        }

        if (isCityGML3 || geometrySupport.supportsLod0MultiSurface(version, target)) {
            GeometryProperty lod0MultiSurface = source.getGeometries()
                    .getFirst(Name.of("lod0MultiSurface", Namespaces.CORE))
                    .orElse(null);
            if (lod0MultiSurface != null) {
                target.setLod0MultiSurface(helper.getGeometryProperty(lod0MultiSurface, MultiSurfacePropertyAdapter.class));
            }
        }

        if (isCityGML3 || geometrySupport.supportsLod1MultiSurface(version, target)) {
            GeometryProperty lod1MultiSurface = source.getGeometries()
                    .getFirst(Name.of("lod1MultiSurface", Namespaces.CORE))
                    .orElse(null);
            if (lod1MultiSurface != null) {
                target.setLod1MultiSurface(helper.getGeometryProperty(lod1MultiSurface, MultiSurfacePropertyAdapter.class));
            }
        }

        if (isCityGML3 || geometrySupport.supportsLod2MultiSurface(version, target)) {
            GeometryProperty lod2MultiSurface = source.getGeometries()
                    .getFirst(Name.of("lod2MultiSurface", Namespaces.CORE))
                    .orElse(null);
            if (lod2MultiSurface != null) {
                target.setLod2MultiSurface(helper.getGeometryProperty(lod2MultiSurface, MultiSurfacePropertyAdapter.class));
            }
        }

        if (isCityGML3 || geometrySupport.supportsLod3MultiSurface(version, target)) {
            GeometryProperty lod3MultiSurface = source.getGeometries()
                    .getFirst(useLod4asLod3 ?
                            Name.of("lod4MultiSurface", Namespaces.DEPRECATED) :
                            Name.of("lod3MultiSurface", Namespaces.CORE))
                    .orElse(null);
            if (lod3MultiSurface != null) {
                target.setLod3MultiSurface(helper.getGeometryProperty(lod3MultiSurface, MultiSurfacePropertyAdapter.class));
            }
        }

        if (helper.getCityGMLVersion() == CityGMLVersion.v2_0
                && source.getGeometries().containsNamespace(Namespaces.DEPRECATED)) {
            GeometryProperty lod4MultiSurface = source.getGeometries()
                    .getFirst(Name.of("lod4MultiSurface", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod4MultiSurface != null) {
                target.getDeprecatedProperties().setLod4MultiSurface(
                        helper.getGeometryProperty(lod4MultiSurface, MultiSurfacePropertyAdapter.class));
            }
        }

        FeatureProperty pointCloud = source.getFeatures().getFirst(Name.of("pointCloud", Namespaces.CORE)).orElse(null);
        if (pointCloud != null) {
            target.setPointCloud(helper.getObjectProperty(pointCloud, AbstractPointCloudPropertyAdapter.class));
        }
    }
}
