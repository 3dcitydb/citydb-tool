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
import org.citydb.model.common.InlineOrByReferenceProperty;

import java.util.Objects;
import java.util.Optional;

public class SurfaceDataProperty extends Child implements InlineOrByReferenceProperty<SurfaceData<?>> {
    private SurfaceData<?> surfaceData;
    private String reference;

    private SurfaceDataProperty(SurfaceData<?> surfaceData) {
        Objects.requireNonNull(surfaceData, "The surface data must not be null.");
        this.surfaceData = asChild(surfaceData);
    }

    private SurfaceDataProperty(String reference) {
        Objects.requireNonNull(reference, "The reference must not be null.");
        this.reference = reference;
    }

    public static SurfaceDataProperty of(SurfaceData<?> surfaceData) {
        return new SurfaceDataProperty(surfaceData);
    }

    public static SurfaceDataProperty of(String reference) {
        return new SurfaceDataProperty(reference);
    }

    public static SurfaceDataProperty asReference(SurfaceData<?> surfaceData) {
        Objects.requireNonNull(surfaceData, "The referenced surface data must not be null.");
        return new SurfaceDataProperty(surfaceData.getOrCreateObjectId()).setReference(surfaceData);
    }

    @Override
    public Optional<SurfaceData<?>> getObject() {
        return Optional.ofNullable(surfaceData);
    }

    @Override
    public SurfaceDataProperty setObject(SurfaceData<?> surfaceData) {
        if (surfaceData != null) {
            this.surfaceData = asChild(surfaceData);
            reference = null;
        }

        return this;
    }

    @Override
    public Optional<String> getReference() {
        return Optional.ofNullable(reference);
    }

    @Override
    public SurfaceDataProperty setReference(String reference) {
        if (reference != null) {
            this.reference = reference;
            surfaceData = null;
        }

        return this;
    }

    @Override
    public SurfaceDataProperty setReference(SurfaceData<?> surfaceData) {
        if (surfaceData != null) {
            reference = surfaceData.getOrCreateObjectId();
            this.surfaceData = surfaceData;
        }

        return this;
    }

    @Override
    public boolean isInline() {
        return reference == null;
    }

    @Override
    public boolean isReferenced() {
        return reference != null;
    }

    public boolean removeFromParent() {
        Child parent = getParent().orElse(null);
        if (parent instanceof Appearance appearance) {
            return appearance.getSurfaceData().remove(this);
        } else {
            return false;
        }
    }
}
