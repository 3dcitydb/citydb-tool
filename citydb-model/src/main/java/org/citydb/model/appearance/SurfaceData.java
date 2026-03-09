/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
