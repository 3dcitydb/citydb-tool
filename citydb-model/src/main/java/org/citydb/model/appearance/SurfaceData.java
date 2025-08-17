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

package org.citydb.model.appearance;

import org.citydb.model.common.Child;
import org.citydb.model.common.Identifiable;
import org.citydb.model.common.Name;
import org.citydb.model.common.Visitable;
import org.citydb.model.geometry.Surface;

import java.util.List;
import java.util.Optional;

public abstract class SurfaceData<T extends SurfaceData<?>> extends Child implements Identifiable, Visitable {
    private String objectId;
    private String identifier;
    private String identifierCodeSpace;
    private Boolean isFront;

    abstract T self();

    public abstract Name getName();

    @Override
    public Optional<String> getObjectId() {
        return Optional.ofNullable(objectId);
    }

    @Override
    public T setObjectId(String objectId) {
        this.objectId = objectId;
        return self();
    }

    @Override
    public Optional<String> getIdentifier() {
        return Optional.ofNullable(identifier);
    }

    @Override
    public T setIdentifier(String identifier) {
        this.identifier = identifier;
        return self();
    }

    @Override
    public Optional<String> getIdentifierCodeSpace() {
        return Optional.ofNullable(identifierCodeSpace);
    }

    @Override
    public T setIdentifierCodeSpace(String identifierCodeSpace) {
        this.identifierCodeSpace = identifierCodeSpace;
        return self();
    }

    public Optional<Boolean> isFront() {
        return Optional.ofNullable(isFront);
    }

    public T setIsFront(Boolean isFront) {
        this.isFront = isFront;
        return self();
    }

    public abstract List<Surface<?>> getTargets();
}
