/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.appearance;

import org.citydb.model.appearance.TextureCoordinate;
import org.citydb.model.geometry.LinearRing;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-feature appearance data keyed by {@link LinearRing} identity. Produced by
 * {@link AppearanceExtractor} and consumed by the triangulation pipeline plus
 * the format-specific encoders. All three maps are format-neutral and meant to
 * be consumed by any visualization writer: the 3D Tiles GLB encoder reads
 * {@code ringColors} into per-vertex {@code COLOR_0}; the I3S encoder reads it
 * into a Draco {@code COLOR} attribute. Each map is {@code null} rather than
 * empty when the feature carries no data of that kind, so consumers can branch
 * with a single null check.
 * <p>
 * {@code ringColors} RGBA values are kept in their authored
 * <strong>sRGB display space</strong> — the two writers handle gamma
 * differently, so the conversion is left to each consumer:
 * <ul>
 *   <li>The 3D Tiles GLB encoder applies sRGB→linear before baking into
 *       {@code COLOR_0}, since glTF mandates linear color space and
 *       CesiumJS does linear→sRGB on output.</li>
 *   <li>The I3S encoder writes the raw sRGB values straight into the
 *       Draco {@code COLOR} attribute, since CesiumJS's I3S loader
 *       treats {@code COLOR_0} as already-sRGB and skips that
 *       conversion.</li>
 * </ul>
 * Alpha is treated as a numeric scalar (not a color channel) and is
 * left as authored regardless.
 */
public record RingAppearance(Map<LinearRing, List<TextureCoordinate>> texCoords,
                             Map<LinearRing, Integer> ringTextureIds,
                             Map<LinearRing, float[]> ringColors) {
    private static final RingAppearance EMPTY = new RingAppearance(null, null, null);

    public boolean isEmpty() {
        return texCoords == null && ringColors == null;
    }

    public static RingAppearance empty() {
        return EMPTY;
    }

    /**
     * Build a new {@code RingAppearance} whose ring-keys are mapped through
     * {@code ringMap}. Used by the implicit-instance flow: after
     * {@code ImplicitInstanceTransformer} deep-copies a prototype and produces
     * a prototype-ring → instance-ring identity bridge, this method rewrites
     * the prototype's appearance maps onto the instance's ring identities.
     * <p>
     * Entries whose key is absent from {@code ringMap} are dropped — that
     * matches the implicit-instance contract where every prototype ring is
     * expected to have a counterpart in the copy.
     */
    public RingAppearance remapKeys(Map<LinearRing, LinearRing> ringMap) {
        if (isEmpty()) {
            return empty();
        }
        return new RingAppearance(
                remap(texCoords, ringMap),
                remap(ringTextureIds, ringMap),
                remap(ringColors, ringMap));
    }

    private static <V> Map<LinearRing, V> remap(Map<LinearRing, V> source,
                                                Map<LinearRing, LinearRing> ringMap) {
        if (source == null) {
            return null;
        }
        Map<LinearRing, V> out = new IdentityHashMap<>(source.size());
        for (Map.Entry<LinearRing, V> e : source.entrySet()) {
            LinearRing target = ringMap.get(e.getKey());
            if (target != null) {
                out.put(target, e.getValue());
            }
        }
        return out;
    }
}
