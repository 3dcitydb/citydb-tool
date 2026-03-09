/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.transportation;

import org.citydb.io.citygml.adapter.core.AbstractThematicSurfaceAdapter;
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
import org.citygml4j.core.model.transportation.AuxiliaryTrafficArea;

@DatabaseType(name = "AuxiliaryTrafficArea", namespace = Namespaces.TRANSPORTATION)
public class AuxiliaryTrafficAreaAdapter extends AbstractThematicSurfaceAdapter<AuxiliaryTrafficArea> {

    @Override
    public Feature createModel(AuxiliaryTrafficArea source) throws ModelBuildException {
        return Feature.of(FeatureType.AUXILIARY_TRAFFIC_AREA);
    }

    @Override
    public void build(AuxiliaryTrafficArea source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.TRANSPORTATION);

        if (source.getSurfaceMaterial() != null) {
            helper.addAttribute(Name.of("surfaceMaterial", Namespaces.TRANSPORTATION), source.getSurfaceMaterial(),
                    target, CodeAdapter.class);
        }
    }

    @Override
    public AuxiliaryTrafficArea createObject(Feature source) throws ModelSerializeException {
        return new AuxiliaryTrafficArea();
    }

    @Override
    public void serialize(Feature source, AuxiliaryTrafficArea target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.TRANSPORTATION);

        Attribute classifier = source.getAttributes()
                .getFirst(Name.of("surfaceMaterial", Namespaces.TRANSPORTATION))
                .orElse(null);
        if (classifier != null) {
            target.setSurfaceMaterial(helper.getAttribute(classifier, CodeAdapter.class));
        }
    }

    @Override
    protected void configureSerializer(SpaceBoundaryGeometrySupport<AuxiliaryTrafficArea> geometrySupport) {
        geometrySupport.withLod2MultiSurface()
                .withLod3MultiSurface();
    }
}
