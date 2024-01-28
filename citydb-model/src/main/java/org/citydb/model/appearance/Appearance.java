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

import org.citydb.model.common.ChildList;
import org.citydb.model.common.Describable;
import org.citydb.model.common.ModelObject;
import org.citydb.model.common.Visitor;

import java.util.List;
import java.util.Optional;

public class Appearance extends ModelObject<Appearance> implements Describable<AppearanceDescriptor> {
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

    @Override
    protected Appearance self() {
        return this;
    }
}
