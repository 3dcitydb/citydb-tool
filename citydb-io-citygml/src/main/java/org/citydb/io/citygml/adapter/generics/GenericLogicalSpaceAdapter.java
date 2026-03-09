/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.generics;

import org.citydb.io.citygml.adapter.core.AbstractLogicalSpaceAdapter;
import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citygml4j.core.model.generics.GenericLogicalSpace;

@DatabaseType(name = "GenericLogicalSpace", namespace = Namespaces.GENERICS)
public class GenericLogicalSpaceAdapter extends AbstractLogicalSpaceAdapter<GenericLogicalSpace> {

    @Override
    public Feature createModel(GenericLogicalSpace source) throws ModelBuildException {
        return Feature.of(FeatureType.GENERIC_LOGICAL_SPACE);
    }

    @Override
    public void build(GenericLogicalSpace source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.GENERICS);
    }

    @Override
    public GenericLogicalSpace createObject(Feature source) throws ModelSerializeException {
        return new GenericLogicalSpace();
    }

    @Override
    public void serialize(Feature source, GenericLogicalSpace target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.GENERICS);
    }
}
