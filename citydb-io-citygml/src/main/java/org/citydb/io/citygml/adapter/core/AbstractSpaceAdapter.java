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

package org.citydb.io.citygml.adapter.core;

import org.citydb.io.citygml.adapter.geometry.builder.Lod;
import org.citydb.io.citygml.adapter.geometry.serializer.MultiCurvePropertyAdapter;
import org.citydb.io.citygml.adapter.geometry.serializer.MultiSurfacePropertyAdapter;
import org.citydb.io.citygml.adapter.geometry.serializer.PointPropertyAdapter;
import org.citydb.io.citygml.adapter.geometry.serializer.SolidPropertyAdapter;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.*;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.DataType;
import org.citydb.model.property.FeatureProperty;
import org.citydb.model.property.GeometryProperty;
import org.citydb.model.walker.ModelWalker;
import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.core.*;
import org.citygml4j.core.model.generics.GenericThematicSurface;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractSpaceAdapter<T extends AbstractSpace> extends AbstractCityObjectAdapter<T> {
    final SpaceGeometrySupport<T> geometrySupport = new SpaceGeometrySupport<>();

    public AbstractSpaceAdapter() {
        configureSerializer(geometrySupport);
    }

    @Override
    public void build(T source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.getSpaceType() != null) {
            target.addAttribute(Attribute.of(Name.of("spaceType", Namespaces.CORE), DataType.STRING)
                    .setStringValue(source.getSpaceType().toValue()));
        }

        if (source.isSetVolumes()) {
            for (QualifiedVolumeProperty property : source.getVolumes()) {
                if (property != null) {
                    helper.addAttribute(Name.of("volume", Namespaces.CORE), property.getObject(), target,
                            QualifiedVolumeAdapter.class);
                }
            }
        }

        if (source.isSetAreas()) {
            for (QualifiedAreaProperty property : source.getAreas()) {
                if (property != null) {
                    helper.addAttribute(Name.of("area", Namespaces.CORE), property.getObject(), target,
                            QualifiedAreaAdapter.class);
                }
            }
        }

        if (source.isSetBoundaries()) {
            for (AbstractSpaceBoundaryProperty property : source.getBoundaries()) {
                helper.addContainedFeature(Name.of("boundary", Namespaces.CORE), property, target);
            }
        }

        if (source.getLod0Point() != null) {
            helper.addPointGeometry(Name.of("lod0Point", Namespaces.CORE), source.getLod0Point(), Lod.of(0), target);
        }

        if (source.getLod0MultiSurface() != null) {
            helper.addSurfaceGeometry(Name.of("lod0MultiSurface", Namespaces.CORE), source.getLod0MultiSurface(),
                    Lod.of(0), target);
        }

        if (source.getLod0MultiCurve() != null) {
            helper.addCurveGeometry(Name.of("lod0MultiCurve", Namespaces.CORE), source.getLod0MultiCurve(),
                    Lod.of(0), target);
        }

        if (source.getLod1Solid() != null) {
            helper.addSolidGeometry(Name.of("lod1Solid", Namespaces.CORE), source.getLod1Solid(),
                    Lod.of(1), target);
        }

        if (source.getLod2Solid() != null) {
            helper.addSolidGeometry(Name.of("lod2Solid", Namespaces.CORE), source.getLod2Solid(),
                    Lod.of(2), target);
        }

        if (source.getLod2MultiSurface() != null) {
            helper.addSurfaceGeometry(Name.of("lod2MultiSurface", Namespaces.CORE), source.getLod2MultiSurface(),
                    Lod.of(2), target);
        }

        if (source.getLod2MultiCurve() != null) {
            helper.addCurveGeometry(Name.of("lod2MultiCurve", Namespaces.CORE), source.getLod2MultiCurve(),
                    Lod.of(2), target);
        }

        if (source.getLod3Solid() != null) {
            helper.addSolidGeometry(Name.of("lod3Solid", Namespaces.CORE), source.getLod3Solid(),
                    Lod.of(3), target);
        }

        if (source.getLod3MultiSurface() != null) {
            helper.addSurfaceGeometry(Name.of("lod3MultiSurface", Namespaces.CORE), source.getLod3MultiSurface(),
                    Lod.of(3), target);
        }

        if (source.getLod3MultiCurve() != null) {
            helper.addCurveGeometry(Name.of("lod3MultiCurve", Namespaces.CORE), source.getLod3MultiCurve(),
                    Lod.of(3), target);
        }
    }

    @Override
    public void serialize(Feature source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        source.getAttributes().getFirst(Name.of("spaceType", Namespaces.CORE))
                .flatMap(Attribute::getStringValue)
                .ifPresent(value -> target.setSpaceType(SpaceType.fromValue(value)));

        for (Attribute attribute : source.getAttributes().get(Name.of("volume", Namespaces.CORE))) {
            target.getVolumes().add(new QualifiedVolumeProperty(
                    helper.getAttribute(attribute, QualifiedVolumeAdapter.class)));
        }

        for (Attribute attribute : source.getAttributes().get(Name.of("area", Namespaces.CORE))) {
            target.getAreas().add(new QualifiedAreaProperty(
                    helper.getAttribute(attribute, QualifiedAreaAdapter.class)));
        }

        for (FeatureProperty property : source.getFeatures().get(Name.of("boundary", Namespaces.CORE))) {
            target.addBoundary(helper.getObjectProperty(property, AbstractSpaceBoundaryPropertyAdapter.class));
        }

        if (helper.isMapLod1MultiSurfaces()) {
            GeometryProperty property = source.getGeometries()
                    .getFirst(Name.of("lod1MultiSurface", Namespaces.DEPRECATED))
                    .orElse(null);

            if (property == null) {
                property = source.getGeometries()
                        .getFirst(Name.of("lod1Geometry", Namespaces.DEPRECATED))
                        .orElse(null);
            }

            if (property != null) {
                MultiSurface multiSurface = getOrCreateMultiSurface(property);
                if (multiSurface != null) {
                    GenericThematicSurface surface = new GenericThematicSurface();
                    surface.setLod1MultiSurface(helper.getGeometryProperty(multiSurface,
                            MultiSurfacePropertyAdapter.class));
                    target.addBoundary(new AbstractSpaceBoundaryProperty(surface));
                }
            }
        }
    }

    @Override
    public void postSerialize(Feature source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.postSerialize(source, target, helper);
        CityGMLVersion version = helper.getCityGMLVersion();
        boolean isCityGML3 = version == CityGMLVersion.v3_0;
        boolean useLod4asLod3 = helper.isUseLod4AsLod3();

        if (isCityGML3 || geometrySupport.supportsLod0Point(version, target)) {
            GeometryProperty lod0Point = source.getGeometries()
                    .getFirst(Name.of("lod0Point", Namespaces.CORE))
                    .orElse(null);
            if (lod0Point != null) {
                target.setLod0Point(helper.getGeometryProperty(lod0Point, PointPropertyAdapter.class));
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

        if (isCityGML3 || geometrySupport.supportsLod0MultiCurve(version, target)) {
            GeometryProperty lod0MultiCurve = source.getGeometries()
                    .getFirst(Name.of("lod0MultiCurve", Namespaces.CORE))
                    .orElse(null);
            if (lod0MultiCurve != null) {
                target.setLod0MultiCurve(helper.getGeometryProperty(lod0MultiCurve, MultiCurvePropertyAdapter.class));
            }
        }

        if (isCityGML3 || geometrySupport.supportsLod1Solid(version, target)) {
            GeometryProperty lod1Solid = source.getGeometries()
                    .getFirst(Name.of("lod1Solid", Namespaces.CORE))
                    .orElse(null);
            if (lod1Solid != null) {
                target.setLod1Solid(helper.getGeometryProperty(lod1Solid, SolidPropertyAdapter.class));
            }
        }

        if (isCityGML3 || geometrySupport.supportsLod2Solid(version, target)) {
            GeometryProperty lod2Solid = source.getGeometries()
                    .getFirst(Name.of("lod2Solid", Namespaces.CORE))
                    .orElse(null);
            if (lod2Solid != null) {
                target.setLod2Solid(helper.getGeometryProperty(lod2Solid, SolidPropertyAdapter.class));
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

        if (isCityGML3 || geometrySupport.supportsLod2MultiCurve(version, target)) {
            GeometryProperty lod2MultiCurve = source.getGeometries()
                    .getFirst(Name.of("lod2MultiCurve", Namespaces.CORE))
                    .orElse(null);
            if (lod2MultiCurve != null) {
                target.setLod2MultiCurve(helper.getGeometryProperty(lod2MultiCurve, MultiCurvePropertyAdapter.class));
            }
        }

        if (useLod4asLod3) {
            GeometryProperty lod4MultiSolid = source.getGeometries()
                    .getFirst(Name.of("lod4MultiSolid", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod4MultiSolid != null) {
                mapLod4ToLod3(lod4MultiSolid, target, helper);
            }

            GeometryProperty lod4Geometry = source.getGeometries()
                    .getFirst(Name.of("lod4Geometry", Namespaces.DEPRECATED))
                    .orElse(null);
            if (lod4Geometry != null) {
                mapLod4ToLod3(lod4Geometry, target, helper);
            }
        }

        if (isCityGML3 || geometrySupport.supportsLod3Solid(version, target)) {
            GeometryProperty lod3Solid = source.getGeometries()
                    .getFirst(useLod4asLod3 ?
                            Name.of("lod4Solid", Namespaces.DEPRECATED) :
                            Name.of("lod3Solid", Namespaces.CORE))
                    .orElse(null);
            if (lod3Solid != null) {
                target.setLod3Solid(helper.getGeometryProperty(lod3Solid, SolidPropertyAdapter.class));
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

        if (isCityGML3 || geometrySupport.supportsLod3MultiCurve(version, target)) {
            GeometryProperty lod3MultiCurve = source.getGeometries()
                    .getFirst(useLod4asLod3 ?
                            Name.of("lod4MultiCurve", Namespaces.DEPRECATED) :
                            Name.of("lod3MultiCurve", Namespaces.CORE))
                    .orElse(null);
            if (lod3MultiCurve != null) {
                target.setLod3MultiCurve(helper.getGeometryProperty(lod3MultiCurve, MultiCurvePropertyAdapter.class));
            }
        }
    }

    protected void configureSerializer(SpaceGeometrySupport<T> geometrySupport) {
    }

    private MultiSurface getOrCreateMultiSurface(GeometryProperty property) {
        Geometry<?> geometry = property.getObject();
        return switch (geometry.getGeometryType()) {
            case MULTI_SURFACE -> (MultiSurface) geometry;
            case COMPOSITE_SURFACE, TRIANGULATED_SURFACE ->
                    MultiSurface.of(((SurfaceCollection<?>) geometry).getPolygons())
                            .setObjectId(geometry.getObjectId().orElse(null));
            default -> null;
        };
    }

    private void mapLod4ToLod3(GeometryProperty property, AbstractSpace target, ModelSerializerHelper helper) throws ModelSerializeException {
        Geometry<?> geometry = property.getObject();
        switch (geometry.getGeometryType()) {
            case SOLID:
            case COMPOSITE_SOLID:
                target.setLod3Solid(helper.getGeometryProperty(property, SolidPropertyAdapter.class));
                break;
            case MULTI_SURFACE:
                target.setLod3MultiSurface(helper.getGeometryProperty(property, MultiSurfacePropertyAdapter.class));
                break;
            case COMPOSITE_SURFACE:
            case TRIANGULATED_SURFACE:
                geometry = MultiSurface.of(((SurfaceCollection<?>) geometry).getPolygons())
                        .setObjectId(geometry.getObjectId().orElse(null));
                target.setLod3MultiSurface(helper.getGeometryProperty(geometry, MultiSurfacePropertyAdapter.class));
                break;
            case MULTI_LINE_STRING:
                target.setLod3MultiCurve(helper.getGeometryProperty(property, MultiCurvePropertyAdapter.class));
                break;
            case LINE_STRING:
                geometry = MultiLineString.of(List.of((LineString) geometry));
                target.setLod3MultiCurve(helper.getGeometryProperty(geometry, MultiCurvePropertyAdapter.class));
                break;
            case MULTI_SOLID:
                MultiSolid multiSolid = (MultiSolid) geometry;
                if (multiSolid.getSolids().size() == 1) {
                    target.setLod3Solid(helper.getGeometryProperty(multiSolid.getSolids().get(0),
                            SolidPropertyAdapter.class));
                    break;
                }
            default:
                List<Polygon> polygons = new ArrayList<>();
                geometry.accept(new ModelWalker() {
                    @Override
                    public void visit(Polygon polygon) {
                        polygons.add(polygon);
                    }
                });

                if (!polygons.isEmpty()) {
                    geometry = MultiSurface.of(polygons);
                    target.setLod3MultiSurface(helper.getGeometryProperty(geometry, MultiSurfacePropertyAdapter.class));
                }
        }
    }
}
