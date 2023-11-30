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

import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;
import org.citydb.model.common.Visitor;
import org.citydb.model.geometry.Surface;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class X3DMaterial extends SurfaceData<X3DMaterial> {
    private Double shininess;
    private Double transparency;
    private Double ambientIntensity;
    private Color diffuseColor;
    private Color emissiveColor;
    private Color specularColor;
    private Boolean isSmooth;
    private List<Surface<?>> targets;

    private X3DMaterial() {
    }

    public static X3DMaterial newInstance() {
        return new X3DMaterial();
    }

    @Override
    public Name getName() {
        return Name.of("X3DMaterial", Namespaces.APPEARANCE);
    }

    public Optional<Double> getShininess() {
        return Optional.ofNullable(shininess);
    }

    public X3DMaterial setShininess(Double shininess) {
        this.shininess = shininess;
        return this;
    }

    public Optional<Double> getTransparency() {
        return Optional.ofNullable(transparency);
    }

    public X3DMaterial setTransparency(Double transparency) {
        this.transparency = transparency;
        return this;
    }

    public Optional<Double> getAmbientIntensity() {
        return Optional.ofNullable(ambientIntensity);
    }

    public X3DMaterial setAmbientIntensity(Double ambientIntensity) {
        this.ambientIntensity = ambientIntensity;
        return this;
    }

    public Optional<Color> getDiffuseColor() {
        return Optional.ofNullable(diffuseColor);
    }

    public X3DMaterial setDiffuseColor(Color diffuseColor) {
        this.diffuseColor = diffuseColor;
        return this;
    }

    public Optional<Color> getEmissiveColor() {
        return Optional.ofNullable(emissiveColor);
    }

    public X3DMaterial setEmissiveColor(Color emissiveColor) {
        this.emissiveColor = emissiveColor;
        return this;
    }

    public Optional<Color> getSpecularColor() {
        return Optional.ofNullable(specularColor);
    }

    public X3DMaterial setSpecularColor(Color specularColor) {
        this.specularColor = specularColor;
        return this;
    }

    public Optional<Boolean> getIsSmooth() {
        return Optional.ofNullable(isSmooth);
    }

    public X3DMaterial setIsSmooth(Boolean isSmooth) {
        this.isSmooth = isSmooth;
        return this;
    }

    public boolean hasTargets() {
        return targets != null && !targets.isEmpty();
    }

    public List<Surface<?>> getTargets() {
        if (targets == null) {
            targets = new ArrayList<>();
        }

        return targets;
    }

    public X3DMaterial setTargets(List<Surface<?>> targets) {
        this.targets = targets;
        return this;
    }

    public X3DMaterial addTarget(Surface<?> target) {
        if (target != null) {
            getTargets().add(target);
        }

        return this;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    X3DMaterial self() {
        return this;
    }
}
