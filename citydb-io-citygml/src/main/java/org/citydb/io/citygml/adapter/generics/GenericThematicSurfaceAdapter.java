/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.generics;

import org.citydb.io.citygml.adapter.core.AbstractThematicSurfaceAdapter;
import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citygml4j.core.model.generics.GenericThematicSurface;

@DatabaseType(name = "GenericThematicSurface", namespace = Namespaces.GENERICS)
public class GenericThematicSurfaceAdapter extends AbstractThematicSurfaceAdapter<GenericThematicSurface> {

    @Override
    public Feature createModel(GenericThematicSurface source) throws ModelBuildException {
        return Feature.of(FeatureType.GENERIC_THEMATIC_SURFACE);
    }

    @Override
    public void build(GenericThematicSurface source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.GENERICS);
    }

    @Override
    public GenericThematicSurface createObject(Feature source) throws ModelSerializeException {
        return new GenericThematicSurface();
    }

    @Override
    public void serialize(Feature source, GenericThematicSurface target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.GENERICS);
    }
}
