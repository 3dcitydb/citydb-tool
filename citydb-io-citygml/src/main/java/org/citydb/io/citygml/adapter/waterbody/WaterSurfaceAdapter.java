/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.waterbody;

import org.citydb.io.citygml.adapter.core.SpaceBoundaryGeometrySupport;
import org.citydb.io.citygml.adapter.gml.CodeAdapter;
import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citydb.model.property.Attribute;
import org.citygml4j.core.model.waterbody.WaterSurface;

@DatabaseType(name = "WaterSurface", namespace = Namespaces.WATER_BODY)
public class WaterSurfaceAdapter extends AbstractWaterBoundarySurfaceAdapter<WaterSurface> {

    @Override
    public Feature createModel(WaterSurface source) throws ModelBuildException {
        return Feature.of(FeatureType.WATER_SURFACE);
    }

    @Override
    public void build(WaterSurface source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.getWaterLevel() != null) {
            helper.addAttribute(Name.of("waterLevel", Namespaces.WATER_BODY), source.getWaterLevel(), target,
                    CodeAdapter.class);
        }
    }

    @Override
    public WaterSurface createObject(Feature source) throws ModelSerializeException {
        return new WaterSurface();
    }

    @Override
    public void serialize(Feature source, WaterSurface target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        Attribute classifier = source.getAttributes()
                .getFirst(Name.of("waterLevel", Namespaces.WATER_BODY))
                .orElse(null);
        if (classifier != null) {
            target.setWaterLevel(helper.getAttribute(classifier, CodeAdapter.class));
        }
    }

    @Override
    protected void configureSerializer(SpaceBoundaryGeometrySupport<WaterSurface> geometrySupport) {
        geometrySupport.withLod2MultiSurface()
                .withLod3MultiSurface();
    }
}
