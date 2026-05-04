/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;

import java.util.List;

/**
 * Geometry definition with a single Draco-compressed geometryBuffer. The
 * five layouts cover the cross-product of textured/untextured and
 * with/without baked vertex colors, plus a shaded-colored variant used
 * by per-feature-type styling.
 * <p>
 * NORMAL emission rules:
 * <ul>
 *   <li>{@link DracoBuffer#untextured()} — NORMAL present (shaded plain path).</li>
 *   <li>{@link DracoBuffer#textured()} / {@link DracoBuffer#texturedColored()} —
 *       no NORMAL (unlit so the texture sample isn't dimmed by Lambertian).</li>
 *   <li>{@link DracoBuffer#colored()} — no NORMAL (unlit X3DMaterial path:
 *       authored thematic colors render at full intensity).</li>
 *   <li>{@link DracoBuffer#coloredShaded()} — NORMAL <i>and</i> COLOR present.
 *       Used when per-feature-type styling routes a node's plain triangles
 *       through baked vertex colors but still wants Lambertian shading so
 *       a uniform-coloured surface (e.g. a red roof) shows 3D form.</li>
 * </ul>
 * The feature-index attribute carries per-vertex feature identification for
 * picking (via injected {@code i3s-feature-ids} Draco metadata).
 */
@JSONType(alphabetic = false)
public class GeometryDefinition {
    private List<Object> geometryBuffers;

    public static GeometryDefinition untextured() {
        GeometryDefinition def = new GeometryDefinition();
        def.geometryBuffers = List.of(DracoBuffer.untextured());
        return def;
    }

    public static GeometryDefinition textured() {
        GeometryDefinition def = new GeometryDefinition();
        def.geometryBuffers = List.of(DracoBuffer.textured());
        return def;
    }

    public static GeometryDefinition texturedColored() {
        GeometryDefinition def = new GeometryDefinition();
        def.geometryBuffers = List.of(DracoBuffer.texturedColored());
        return def;
    }

    public static GeometryDefinition colored() {
        GeometryDefinition def = new GeometryDefinition();
        def.geometryBuffers = List.of(DracoBuffer.colored());
        return def;
    }

    public static GeometryDefinition coloredShaded() {
        GeometryDefinition def = new GeometryDefinition();
        def.geometryBuffers = List.of(DracoBuffer.coloredShaded());
        return def;
    }

    public static class DracoBuffer {
        private CompressedAttributes compressedAttributes;

        public static DracoBuffer untextured() {
            return wrap(List.of("position", "normal", "feature-index"));
        }

        public static DracoBuffer textured() {
            return wrap(List.of("position", "uv0", "feature-index"));
        }

        public static DracoBuffer texturedColored() {
            return wrap(List.of("position", "uv0", "color", "feature-index"));
        }

        public static DracoBuffer colored() {
            return wrap(List.of("position", "color", "feature-index"));
        }

        public static DracoBuffer coloredShaded() {
            return wrap(List.of("position", "normal", "color", "feature-index"));
        }

        private static DracoBuffer wrap(List<String> attrs) {
            DracoBuffer buffer = new DracoBuffer();
            buffer.compressedAttributes = new CompressedAttributes("draco", attrs);
            return buffer;
        }
    }

    @JSONType(alphabetic = false)
    public record CompressedAttributes(String encoding, List<String> attributes) {
    }
}
