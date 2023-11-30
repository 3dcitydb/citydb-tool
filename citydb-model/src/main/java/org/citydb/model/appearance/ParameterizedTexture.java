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
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.geometry.Surface;

import java.util.*;
import java.util.stream.Collectors;

public class ParameterizedTexture extends Texture<ParameterizedTexture> {
    private Map<LinearRing, List<TextureCoordinate>> textureCoordinates;
    private Map<Surface<?>, List<Double>> worldToTextureMappings;

    private ParameterizedTexture() {
    }

    public static ParameterizedTexture newInstance() {
        return new ParameterizedTexture();
    }

    @Override
    public Name getName() {
        return Name.of("ParameterizedTexture", Namespaces.APPEARANCE);
    }

    public boolean hasTextureCoordinates() {
        return textureCoordinates != null && !textureCoordinates.isEmpty();
    }

    public Map<LinearRing, List<TextureCoordinate>> getTextureCoordinates() {
        if (textureCoordinates == null) {
            textureCoordinates = new IdentityHashMap<>();
        }

        return textureCoordinates;
    }

    public List<TextureCoordinate> getTextureCoordinates(LinearRing linearRing) {
        return textureCoordinates != null ? textureCoordinates.get(linearRing) : null;
    }

    public ParameterizedTexture addTextureCoordinates(LinearRing linearRing, List<TextureCoordinate> textureCoordinates) {
        Objects.requireNonNull(linearRing, "The linear ring must not be null.");
        if (linearRing.getParent().isEmpty()) {
            throw new IllegalArgumentException("The linear ring must belong to a target polygon.");
        }

        getTextureCoordinates().put(linearRing, textureCoordinates);
        return this;
    }

    public boolean hasWorldToTextureMappings() {
        return worldToTextureMappings != null && !worldToTextureMappings.isEmpty();
    }

    public Map<Surface<?>, List<Double>> getWorldToTextureMappings() {
        if (worldToTextureMappings == null) {
            worldToTextureMappings = new IdentityHashMap<>();
        }

        return worldToTextureMappings;
    }

    public List<Double> getWorldToTextureMapping(Surface<?> surface) {
        return worldToTextureMappings != null ? worldToTextureMappings.get(surface) : null;
    }

    public ParameterizedTexture addWorldToTextureMapping(Surface<?> surface, List<Double> transformationMatrix) {
        Objects.requireNonNull(surface, "The surface geometry must not be null.");
        if (transformationMatrix != null && transformationMatrix.size() > 11) {
            getWorldToTextureMappings().put(surface, transformationMatrix.subList(0, 12));
        }

        return this;
    }

    public List<Surface<?>> getTargets() {
        List<Surface<?>> targets = new ArrayList<>();

        if (hasTextureCoordinates()) {
            targets.addAll(textureCoordinates.keySet().stream()
                    .map(linearRing -> linearRing.getParent().orElse(null))
                    .filter(Objects::nonNull)
                    .map(surface -> (Surface<?>) surface)
                    .collect(Collectors.toList()));
        }

        if (hasWorldToTextureMappings()) {
            targets.addAll(worldToTextureMappings.keySet());
        }

        return targets;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    ParameterizedTexture self() {
        return this;
    }
}
