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

import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.serializer.ModelSerializer;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.common.Reference;
import org.citydb.model.feature.Feature;
import org.citydb.model.property.FeatureProperty;
import org.xmlobjects.gml.model.base.AbstractGML;
import org.xmlobjects.gml.model.base.AbstractInlineOrByReferenceProperty;

public abstract class AbstractInlineOrByReferencePropertyAdapter<T extends AbstractInlineOrByReferenceProperty<?>> implements ModelSerializer<FeatureProperty, T> {

    @Override
    public void serialize(FeatureProperty source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        Feature feature = source.getObject().orElse(null);
        if (feature != null) {
            if (helper.lookupAndPut(feature)) {
                target.setHref("#" + feature.getOrCreateObjectId());
            } else {
                target.setInlineObjectIfValid(helper.getObject(feature, AbstractGML.class));
            }
        } else {
            source.getReference().map(Reference::getTarget).ifPresent(reference -> target.setHref("#" + reference));
        }
    }
}
