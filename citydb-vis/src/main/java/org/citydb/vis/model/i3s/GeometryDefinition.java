/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;

import java.util.List;

/**
 * Geometry definition with a single uncompressed legacy I3S 1.9geometry
 * buffer. ArcGIS Pro / Online and CesiumJS both consume the same buffer:
 * Float32 positions (X/Y in degree-offsets from the MBS center, Z in
 * meters), optional Float32 normals, Float32 uv0, optional UInt8 RGBA
 * colors, plus per-feature {@code featureId} (UInt64) and
 * {@code faceRange} (UInt32×2) — Esri runtimes use the per-feature data
 * directly, CesiumJS derives a per-vertex feature index from
 * {@code faceRange} for picking.
 * <p>
 * Each factory below covers a distinct material / vertex-attribute
 * combination, with each non-textured flavour having a shaded (NORMAL
 * present) and an unlit (NORMAL absent) variant selected via
 * {@code --enable-shading}. ArcGIS clients require NORMAL — exports
 * targeted at Pro / Online must pass the flag, otherwise the SLPK fails
 * to load with a red error indicator. CesiumJS works either way; without
 * NORMAL it auto-computes flat normals client-side (controlled via
 * {@code I3SDataProvider.calculateNormals}).
 * <p>
 * Every variant declares {@code uv0} on the buffer — even untextured
 * slots — because CesiumJS's {@code I3SLayer._findBestGeometryBuffers}
 * hardcodes {@code ["position", "uv0"]} as the lookup key. The encoder
 * fills the per-vertex stream with {@code (0, 0)} on untextured triangles.
 */
@JSONType(alphabetic = false)
public class GeometryDefinition {
    private List<Object> geometryBuffers;

    public static GeometryDefinition untextured() {
        return wrap(LegacyBuffer.plain(true));
    }

    public static GeometryDefinition untexturedNoNormal() {
        return wrap(LegacyBuffer.plain(false));
    }

    public static GeometryDefinition textured() {
        return wrap(LegacyBuffer.plain(false));
    }

    public static GeometryDefinition texturedColored() {
        return wrap(LegacyBuffer.colored(false));
    }

    public static GeometryDefinition texturedShaded() {
        return wrap(LegacyBuffer.plain(true));
    }

    public static GeometryDefinition texturedColoredShaded() {
        return wrap(LegacyBuffer.colored(true));
    }

    public static GeometryDefinition colored() {
        return wrap(LegacyBuffer.colored(false));
    }

    public static GeometryDefinition coloredShaded() {
        return wrap(LegacyBuffer.colored(true));
    }

    private static GeometryDefinition wrap(LegacyBuffer legacy) {
        GeometryDefinition def = new GeometryDefinition();
        def.geometryBuffers = List.of(legacy);
        return def;
    }

    /**
     * Uncompressed legacy buffer schema. Binary layout:
     * <pre>
     *   UInt32 LE  vertexCount
     *   UInt32 LE  featureCount
     *   Float32 LE × 3 × vertexCount    positions       (X/Y deg-offset, Z meters)
     *   Float32 LE × 3 × vertexCount    normals         (when --enable-shading)
     *   Float32 LE × 2 × vertexCount    uv0
     *   UInt8         × 4 × vertexCount colors RGBA     (when present)
     *   UInt64 LE     × featureCount    featureIds
     *   UInt32 LE × 2 × featureCount    faceRanges      (start_face, end_face inclusive)
     * </pre>
     * The {@code offset:8} declares the header size; null fields here
     * (e.g. {@code normal} on the unlit variants, {@code color} on
     * untextured slots) are skipped during JSON serialization, mirroring
     * their absence in the binary stream.
     */
    @JSONType(alphabetic = false)
    public static class LegacyBuffer {
        private final int offset = 8;
        private final AttrSchema position = AttrSchema.vertex("Float32", 3);
        private AttrSchema normal;
        private final AttrSchema uv0 = AttrSchema.vertex("Float32", 2);
        private AttrSchema color;
        private final AttrSchema featureId = AttrSchema.feature("UInt64", 1);
        private final AttrSchema faceRange = AttrSchema.feature("UInt32", 2);

        private LegacyBuffer(boolean hasNormal, boolean hasColor) {
            if (hasNormal) normal = AttrSchema.vertex("Float32", 3);
            if (hasColor) color = AttrSchema.vertex("UInt8", 4);
        }

        public static LegacyBuffer plain(boolean hasNormal) {
            return new LegacyBuffer(hasNormal, false);
        }

        public static LegacyBuffer colored(boolean hasNormal) {
            return new LegacyBuffer(hasNormal, true);
        }
    }

    @JSONType(alphabetic = false)
    public static class AttrSchema {
        private final String type;
        private final int component;
        private final String binding;

        private AttrSchema(String type, int component, String binding) {
            this.type = type;
            this.component = component;
            this.binding = binding;
        }

        static AttrSchema vertex(String type, int component) {
            return new AttrSchema(type, component, null);
        }

        static AttrSchema feature(String type, int component) {
            return new AttrSchema(type, component, "per-feature");
        }
    }
}
