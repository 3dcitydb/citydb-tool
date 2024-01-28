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

package org.citydb.io.citygml.adapter.gml;

import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.builder.ModelBuilder;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.serializer.ModelSerializer;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.property.Attribute;
import org.xmlobjects.gml.model.base.AbstractGML;
import org.xmlobjects.gml.model.basictypes.Code;
import org.xmlobjects.gml.model.basictypes.CodeWithAuthority;

public abstract class AbstractGMLAdapter<T extends AbstractGML> implements ModelBuilder<T, Feature>, ModelSerializer<Feature, T> {

    @Override
    public void build(T source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        target.setObjectId(source.getId());

        if (source.getDescription() != null) {
            helper.addAttribute(Name.of("description", Namespaces.CORE), source.getDescription(), target,
                    StringOrRefAdapter.class);
        }

        if (source.getDescriptionReference() != null) {
            helper.addAttribute(Name.of("descriptionReference", Namespaces.CORE), source.getDescriptionReference(),
                    target, ReferenceAttributeAdapter.class);
        }

        if (source.getIdentifier() != null && source.getIdentifier().getValue() != null) {
            target.setIdentifier(source.getIdentifier().getValue())
                    .setIdentifierCodeSpace(source.getIdentifier().getCodeSpace());
        }

        if (source.isSetNames()) {
            for (Code name : source.getNames()) {
                helper.addAttribute(Name.of("name", Namespaces.CORE), name, target, CodeAdapter.class);
            }
        }
    }

    @Override
    public void serialize(Feature source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        source.getObjectId().ifPresent(target::setId);

        Attribute description = source.getAttributes().getFirst(Name.of("description", Namespaces.CORE)).orElse(null);
        if (description != null) {
            target.setDescription(helper.getAttribute(description, StringOrRefAdapter.class));
        }

        Attribute descriptionReference = source.getAttributes()
                .getFirst(Name.of("descriptionReference", Namespaces.CORE))
                .orElse(null);
        if (descriptionReference != null) {
            target.setDescriptionReference(helper.getAttribute(descriptionReference, ReferenceAttributeAdapter.class));
        }

        source.getIdentifier().ifPresent(identifier -> target.setIdentifier(
                new CodeWithAuthority(identifier, source.getIdentifierCodeSpace().orElse(null))));

        for (Attribute name : source.getAttributes().get(Name.of("name", Namespaces.CORE))) {
            target.getNames().add(helper.getAttribute(name, CodeAdapter.class));
        }
    }
}
