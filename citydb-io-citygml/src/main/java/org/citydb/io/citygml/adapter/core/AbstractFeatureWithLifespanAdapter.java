/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

package org.citydb.io.citygml.adapter.core;

import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.feature.Feature;
import org.citygml4j.core.model.core.AbstractFeatureWithLifespan;

public abstract class AbstractFeatureWithLifespanAdapter<T extends AbstractFeatureWithLifespan> extends AbstractFeatureAdapter<T> {

    @Override
    public void build(T source, Feature target, ModelBuilderHelper helper) throws ModelBuildException {
        super.build(source, target, helper);
        target.setCreationDate(source.getCreationDate())
                .setTerminationDate(source.getTerminationDate())
                .setValidFrom(source.getValidFrom())
                .setValidTo(source.getValidTo());
    }

    @Override
    public void serialize(Feature source, T target, ModelSerializerHelper helper) throws ModelSerializeException {
        super.serialize(source, target, helper);
        boolean isTopLevel = source.getParent().isEmpty();

        if (isTopLevel) {
            source.getCreationDate().ifPresent(target::setCreationDate);
            source.getTerminationDate().ifPresent(target::setTerminationDate);
            source.getValidFrom().ifPresent(target::setValidFrom);
            source.getValidTo().ifPresent(target::setValidTo);
        }
    }
}
