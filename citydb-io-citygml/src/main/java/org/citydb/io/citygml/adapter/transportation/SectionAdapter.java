/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.transportation;

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
import org.citygml4j.core.model.transportation.Section;

@DatabaseType(name = "Section", namespace = Namespaces.TRANSPORTATION)
public class SectionAdapter extends AbstractTransportationSpaceAdapter<Section> {

    @Override
    public Feature createModel(Section source) throws ModelBuildException {
        return Feature.of(FeatureType.SECTION);
    }

    @Override
    public void build(Section source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);

        if (source.getClassifier() != null) {
            helper.addAttribute(Name.of("class", Namespaces.TRANSPORTATION), source.getClassifier(), target,
                    CodeAdapter.class);
        }
    }

    @Override
    public Section createObject(Feature source) throws ModelSerializeException {
        return new Section();
    }

    @Override
    public void serialize(Feature source, Section target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);

        Attribute classifier = source.getAttributes()
                .getFirst(Name.of("class", Namespaces.TRANSPORTATION))
                .orElse(null);
        if (classifier != null) {
            target.setClassifier(helper.getAttribute(classifier, CodeAdapter.class));
        }
    }
}
