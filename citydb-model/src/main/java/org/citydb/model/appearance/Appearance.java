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

package org.citydb.model.appearance;

import org.citydb.model.common.*;

import java.util.List;
import java.util.Optional;

public class Appearance extends Child implements Identifiable, Visitable, Describable<AppearanceDescriptor> {
    private String objectId;
    private String identifier;
    private String identifierCodeSpace;
    private String theme;
    private List<SurfaceDataProperty> surfaceData;
    private AppearanceDescriptor descriptor;

    private Appearance() {
    }

    private Appearance(String theme) {
        this.theme = theme;
    }

    public static Appearance newInstance() {
        return new Appearance();
    }

    public static Appearance of(String theme) {
        return new Appearance(theme);
    }

    @Override
    public Optional<String> getObjectId() {
        return Optional.ofNullable(objectId);
    }

    @Override
    public Appearance setObjectId(String objectId) {
        this.objectId = objectId;
        return this;
    }

    @Override
    public Optional<String> getIdentifier() {
        return Optional.ofNullable(identifier);
    }

    @Override
    public Appearance setIdentifier(String identifier) {
        this.identifier = identifier;
        return this;
    }

    @Override
    public Optional<String> getIdentifierCodeSpace() {
        return Optional.ofNullable(identifierCodeSpace);
    }

    @Override
    public Appearance setIdentifierCodeSpace(String identifierCodeSpace) {
        this.identifierCodeSpace = identifierCodeSpace;
        return this;
    }

    public Optional<String> getTheme() {
        return Optional.ofNullable(theme);
    }

    public Appearance setTheme(String theme) {
        this.theme = theme;
        return this;
    }

    public boolean hasSurfaceData() {
        return surfaceData != null && !surfaceData.isEmpty();
    }

    public List<SurfaceDataProperty> getSurfaceData() {
        if (surfaceData == null) {
            surfaceData = new ChildList<>(this);
        }

        return surfaceData;
    }

    public Appearance setSurfaceData(List<SurfaceDataProperty> surfaceData) {
        this.surfaceData = asChild(surfaceData);
        return this;
    }

    @Override
    public Optional<AppearanceDescriptor> getDescriptor() {
        return Optional.ofNullable(descriptor);
    }

    @Override
    public Appearance setDescriptor(AppearanceDescriptor descriptor) {
        this.descriptor = descriptor;
        return this;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }
}
