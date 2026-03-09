/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.construction;

import org.citydb.io.citygml.annotation.DatabaseType;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureType;
import org.citygml4j.core.model.construction.OtherConstruction;

@DatabaseType(name = "OtherConstruction", namespace = Namespaces.CONSTRUCTION)
public class OtherConstructionAdapter extends AbstractConstructionAdapter<OtherConstruction> {

    @Override
    public Feature createModel(OtherConstruction source) throws ModelBuildException {
        return Feature.of(FeatureType.OTHER_CONSTRUCTION);
    }

    @Override
    public void build(OtherConstruction source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.CONSTRUCTION);
    }

    @Override
    public OtherConstruction createObject(Feature source) throws ModelSerializeException {
        return new OtherConstruction();
    }

    @Override
    public void serialize(Feature source, OtherConstruction target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        helper.addStandardObjectClassifiers(source, target, Namespaces.CONSTRUCTION);
    }
}
