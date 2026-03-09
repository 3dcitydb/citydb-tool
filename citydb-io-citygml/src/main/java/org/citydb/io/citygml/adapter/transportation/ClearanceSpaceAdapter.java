/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.transportation;

import org.citydb.io.citygml.adapter.core.AbstractUnoccupiedSpaceAdapter;
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
import org.citygml4j.core.model.transportation.ClearanceSpace;
import org.xmlobjects.gml.model.basictypes.Code;

@DatabaseType(name = "ClearanceSpace", namespace = Namespaces.TRANSPORTATION)
public class ClearanceSpaceAdapter extends AbstractUnoccupiedSpaceAdapter<ClearanceSpace> {

    @Override
    public Feature createModel(ClearanceSpace source) throws ModelBuildException {
        return Feature.of(FeatureType.CLEARANCE_SPACE);
    }

    @Override
    public void build(ClearanceSpace source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.isSetClassifiers()) {
            for (Code classifier : source.getClassifiers()) {
                helper.addAttribute(Name.of("class", Namespaces.TRANSPORTATION), classifier, target, CodeAdapter.class);
            }
        }
    }

    @Override
    public ClearanceSpace createObject(Feature source) throws ModelSerializeException {
        return new ClearanceSpace();
    }

    @Override
    public void serialize(Feature source, ClearanceSpace target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        for (Attribute attribute : source.getAttributes().get(Name.of("class", Namespaces.TRANSPORTATION))) {
            target.getClassifiers().add(helper.getAttribute(attribute, CodeAdapter.class));
        }
    }
}
