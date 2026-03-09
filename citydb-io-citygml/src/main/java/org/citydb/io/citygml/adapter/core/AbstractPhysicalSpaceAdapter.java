/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.core;

import org.citydb.io.citygml.adapter.geometry.builder.Lod;
import org.citydb.io.citygml.adapter.geometry.serializer.MultiCurvePropertyAdapter;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.property.FeatureProperty;
import org.citydb.model.property.GeometryProperty;
import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.core.AbstractPhysicalSpace;

public abstract class AbstractPhysicalSpaceAdapter<T extends AbstractPhysicalSpace> extends AbstractSpaceAdapter<T> {

    @Override
    public void build(T source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.getLod1TerrainIntersectionCurve() != null) {
            helper.addCurveGeometry(Name.of("lod1TerrainIntersectionCurve", Namespaces.CORE),
                    source.getLod1TerrainIntersectionCurve(), Lod.of(1), target);
        }

        if (source.getLod2TerrainIntersectionCurve() != null) {
            helper.addCurveGeometry(Name.of("lod2TerrainIntersectionCurve", Namespaces.CORE),
                    source.getLod2TerrainIntersectionCurve(), Lod.of(2), target);
        }

        if (source.getLod3TerrainIntersectionCurve() != null) {
            helper.addCurveGeometry(Name.of("lod3TerrainIntersectionCurve", Namespaces.CORE),
                    source.getLod3TerrainIntersectionCurve(), Lod.of(3), target);
        }

        if (source.getPointCloud() != null) {
            helper.addContainedFeature(Name.of("pointCloud", Namespaces.CORE), source.getPointCloud(), target);
        }
    }

    @Override
    public void serialize(Feature source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        FeatureProperty pointCloud = source.getFeatures().getFirst(Name.of("pointCloud", Namespaces.CORE)).orElse(null);
        if (pointCloud != null) {
            target.setPointCloud(helper.getObjectProperty(pointCloud, AbstractPointCloudPropertyAdapter.class));
        }
    }

    @Override
    public void postSerialize(Feature source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.postSerialize(source, target, helper);
        CityGMLVersion version = helper.getCityGMLVersion();
        boolean isCityGML3 = version == CityGMLVersion.v3_0;

        if (isCityGML3 || geometrySupport.supportsLod1TerrainIntersectionCurve(version, target)) {
            GeometryProperty lod1TerrainIntersectionCurve = source.getGeometries()
                    .getFirst(Name.of("lod1TerrainIntersectionCurve", Namespaces.CORE))
                    .orElse(null);
            if (lod1TerrainIntersectionCurve != null) {
                target.setLod1TerrainIntersectionCurve(
                        helper.getGeometryProperty(lod1TerrainIntersectionCurve, MultiCurvePropertyAdapter.class));
            }
        }

        if (isCityGML3 || geometrySupport.supportsLod2TerrainIntersectionCurve(version, target)) {
            GeometryProperty lod2TerrainIntersectionCurve = source.getGeometries()
                    .getFirst(Name.of("lod2TerrainIntersectionCurve", Namespaces.CORE))
                    .orElse(null);
            if (lod2TerrainIntersectionCurve != null) {
                target.setLod2TerrainIntersectionCurve(
                        helper.getGeometryProperty(lod2TerrainIntersectionCurve, MultiCurvePropertyAdapter.class));
            }
        }

        if (isCityGML3 || geometrySupport.supportsLod3TerrainIntersectionCurve(version, target)) {
            GeometryProperty lod3TerrainIntersectionCurve = source.getGeometries()
                    .getFirst(helper.isUseLod4AsLod3() ?
                            Name.of("lod4TerrainIntersectionCurve", Namespaces.DEPRECATED) :
                            Name.of("lod3TerrainIntersectionCurve", Namespaces.CORE))
                    .orElse(null);
            if (lod3TerrainIntersectionCurve != null) {
                target.setLod3TerrainIntersectionCurve(
                        helper.getGeometryProperty(lod3TerrainIntersectionCurve, MultiCurvePropertyAdapter.class));
            }
        }
    }
}
