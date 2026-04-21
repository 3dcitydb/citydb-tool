/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.pipeline;

import org.citydb.model.appearance.Appearance;
import org.citydb.model.appearance.ParameterizedTexture;
import org.citydb.model.appearance.SurfaceData;
import org.citydb.model.appearance.SurfaceDataProperty;
import org.citydb.model.appearance.TextureCoordinate;
import org.citydb.model.common.ExternalFile;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.property.AppearanceProperty;
import org.citydb.vis.store.TextureStore;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts texture coordinates and texture image references from a
 * {@link Feature}'s appearances on the caller thread. Each
 * {@link ParameterizedTexture} maps specific {@link LinearRing} instances to
 * UV coordinates and references a texture image; per-ring texture ids are
 * tracked so each polygon surface ends up with its own texture in the atlas.
 */
public final class AppearanceExtractor {
    public record Result(Map<LinearRing, List<TextureCoordinate>> texCoords,
                         Map<LinearRing, Integer> ringTextureIds) {
        public boolean isEmpty() {
            return texCoords == null;
        }

        public static Result empty() {
            return new Result(null, null);
        }
    }

    private AppearanceExtractor() {
    }

    public static Result extract(Feature feature, TextureStore textureStore) {
        if (!feature.hasAppearances()) {
            return Result.empty();
        }

        Map<LinearRing, List<TextureCoordinate>> texCoordMap = new IdentityHashMap<>();
        Map<LinearRing, Integer> ringTextureMap = new IdentityHashMap<>();

        for (AppearanceProperty ap : feature.getAppearances().getAll()) {
            Appearance appearance = ap.getObject();
            if (appearance == null) continue;
            for (SurfaceDataProperty sdp : appearance.getSurfaceData()) {
                SurfaceData<?> sd = sdp.getObject().orElse(null);
                if (sd instanceof ParameterizedTexture pt && pt.hasTextureCoordinates()) {
                    int ptTextureId = -1;
                    ExternalFile img = pt.getTextureImage().orElse(null);
                    if (img != null) {
                        ptTextureId = textureStore.register(img.getFileLocation());
                    }
                    Map<LinearRing, List<TextureCoordinate>> ptCoords =
                            pt.getTextureCoordinates();
                    texCoordMap.putAll(ptCoords);
                    if (ptTextureId >= 0) {
                        for (LinearRing ring : ptCoords.keySet()) {
                            ringTextureMap.put(ring, ptTextureId);
                        }
                    }
                }
            }
        }

        if (texCoordMap.isEmpty()) {
            return Result.empty();
        }
        return new Result(texCoordMap, ringTextureMap);
    }
}
