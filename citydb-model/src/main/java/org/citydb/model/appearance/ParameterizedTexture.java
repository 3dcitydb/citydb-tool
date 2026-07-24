/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.appearance;

import org.citydb.model.common.*;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.geometry.Surface;
import org.citydb.model.util.CopySession;

import java.util.*;

public class ParameterizedTexture extends Texture<ParameterizedTexture> {
    private Map<LinearRing, List<TextureCoordinate>> textureCoordinates;
    private Map<Surface<?>, Matrix3x4> worldToTextureMappings;

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
        if (textureCoordinates != null) {
            Objects.requireNonNull(linearRing, "The linear ring must not be null.");
            if (linearRing.getParent().isEmpty()) {
                throw new IllegalArgumentException("The linear ring must belong to a target polygon.");
            }

            getTextureCoordinates().put(linearRing, textureCoordinates);
        }

        return this;
    }

    public boolean hasWorldToTextureMappings() {
        return worldToTextureMappings != null && !worldToTextureMappings.isEmpty();
    }

    public Map<Surface<?>, Matrix3x4> getWorldToTextureMappings() {
        if (worldToTextureMappings == null) {
            worldToTextureMappings = new IdentityHashMap<>();
        }

        return worldToTextureMappings;
    }

    public Matrix3x4 getWorldToTextureMapping(Surface<?> surface) {
        return worldToTextureMappings != null ? worldToTextureMappings.get(surface) : null;
    }

    public ParameterizedTexture addWorldToTextureMapping(Surface<?> surface, Matrix3x4 transformationMatrix) {
        if (transformationMatrix != null) {
            Objects.requireNonNull(surface, "The surface geometry must not be null.");
            getWorldToTextureMappings().put(surface, transformationMatrix);
        }

        return this;
    }

    public ParameterizedTexture addWorldToTextureMapping(Surface<?> surface, List<Double> transformationMatrix) {
        if (transformationMatrix != null && transformationMatrix.size() > 11) {
            addWorldToTextureMapping(surface, Matrix3x4.ofRowMajor(transformationMatrix));
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
                    .toList());
        }

        if (hasWorldToTextureMappings()) {
            targets.addAll(worldToTextureMappings.keySet());
        }

        return targets;
    }

    @Override
    protected ParameterizedTexture createClone(CopySession session) {
        return new ParameterizedTexture();
    }

    @Override
    protected void copyPropertiesTo(Child clone, CopySession session) {
        ParameterizedTexture texture = (ParameterizedTexture) clone;
        super.copyPropertiesTo(texture, session);

        if (textureCoordinates != null) {
            texture.textureCoordinates = new IdentityHashMap<>(textureCoordinates.size());
            for (Map.Entry<LinearRing, List<TextureCoordinate>> entry : textureCoordinates.entrySet()) {
                List<TextureCoordinate> coordinates = new ArrayList<>(entry.getValue().size());
                for (TextureCoordinate coordinate : entry.getValue()) {
                    coordinates.add(coordinate.copy());
                }

                texture.textureCoordinates.put(copy(entry.getKey(), session), coordinates);
            }
        }

        if (worldToTextureMappings != null) {
            texture.worldToTextureMappings = new IdentityHashMap<>(worldToTextureMappings.size());
            for (Map.Entry<Surface<?>, Matrix3x4> entry : worldToTextureMappings.entrySet()) {
                texture.worldToTextureMappings.put(copy(entry.getKey(), session), entry.getValue().copy());
            }
        }
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
