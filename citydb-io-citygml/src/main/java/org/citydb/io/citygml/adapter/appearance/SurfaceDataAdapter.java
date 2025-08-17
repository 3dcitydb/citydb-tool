/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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

package org.citydb.io.citygml.adapter.appearance;

import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.builder.ModelBuilder;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.serializer.ModelSerializer;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.appearance.SurfaceData;
import org.citygml4j.core.model.appearance.AbstractSurfaceData;
import org.xmlobjects.gml.model.basictypes.CodeWithAuthority;

public abstract class SurfaceDataAdapter<T extends SurfaceData<?>, R extends AbstractSurfaceData> implements ModelBuilder<R, T>, ModelSerializer<T, R> {

    public abstract R createObject(T source);

    @Override
    public void build(R source, T target, ModelBuilderHelper helper) throws ModelBuildException {
        target.setObjectId(source.getId());

        if (source.isSetIsFront()) {
            target.setIsFront(source.getIsFront());
        }

        if (source.getIdentifier() != null && source.getIdentifier().getValue() != null) {
            target.setIdentifier(source.getIdentifier().getValue())
                    .setIdentifierCodeSpace(source.getIdentifier().getCodeSpace());
        }
    }

    @Override
    public void serialize(T source, R target, ModelSerializerHelper helper) throws ModelSerializeException {
        source.getObjectId().ifPresent(target::setId);
        source.isFront().ifPresent(target::setIsFront);

        source.getIdentifier().ifPresent(identifier -> target.setIdentifier(
                new CodeWithAuthority(identifier, source.getIdentifierCodeSpace().orElse(null))));
    }
}
