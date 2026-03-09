/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
        return new SurfaceDataProperty(surfaceData.getOrCreateObjectId());
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
        return surfaceData != null ? setReference(surfaceData.getOrCreateObjectId()) : this;
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
