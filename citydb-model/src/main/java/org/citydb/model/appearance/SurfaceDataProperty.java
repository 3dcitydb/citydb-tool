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

package org.citydb.model.appearance;

import org.citydb.model.common.Child;
import org.citydb.model.common.InlineOrByReferenceProperty;
import org.citydb.model.common.Reference;

import java.util.Objects;
import java.util.Optional;

public class SurfaceDataProperty extends Child implements InlineOrByReferenceProperty<SurfaceData<?>> {
    private final SurfaceData<?> surfaceData;
    private final Reference reference;

    private SurfaceDataProperty(SurfaceData<?> surfaceData) {
        Objects.requireNonNull(surfaceData, "The surface data must not be null.");
        this.surfaceData = asChild(surfaceData);
        reference = null;
    }

    private SurfaceDataProperty(Reference reference) {
        Objects.requireNonNull(reference, "The reference must not be null.");
        this.reference = asChild(reference);
        surfaceData = null;
    }

    public static SurfaceDataProperty of(SurfaceData<?> surfaceData) {
        return new SurfaceDataProperty(surfaceData);
    }

    public static SurfaceDataProperty of(Reference reference) {
        return new SurfaceDataProperty(reference);
    }

    @Override
    public Optional<SurfaceData<?>> getObject() {
        return Optional.ofNullable(surfaceData);
    }

    @Override
    public Optional<Reference> getReference() {
        return Optional.ofNullable(reference);
    }
}
