/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;

import java.util.List;

/**
 * Geometry definition with a single Draco-compressed geometryBuffer. The
 * layouts cover the cross-product of textured/untextured and with/without
 * baked vertex colors, with each non-textured flavour having both a
 * shaded (NORMAL present) and an unlit (NORMAL absent) variant selected
 * via {@code --enable-shading}.
 * <p>
 * NORMAL emission rules:
 * <ul>
 *   <li>{@link DracoBuffer#untextured()} — NORMAL present (plain shaded).</li>
 *   <li>{@link DracoBuffer#untexturedNoNormal()} — NORMAL absent (plain
 *       unlit; selected when {@code --enable-shading} is off).</li>
 *   <li>{@link DracoBuffer#textured()} / {@link DracoBuffer#texturedColored()} —
 *       no NORMAL; textured path under default {@code --enable-shading=false}
 *       (unlit so the texture sample isn't dimmed by Lambertian).</li>
 *   <li>{@link DracoBuffer#texturedShaded()} /
 *       {@link DracoBuffer#texturedColoredShaded()} — NORMAL present;
 *       textured path under {@code --enable-shading=true}. Truly-textured
 *       vertices carry the local ENU "up" direction (in ECEF) instead of
 *       the polygon's real geometric normal, so all textured triangles
 *       in the node share one Lambertian factor — equally lit walls and
 *       roofs, no per-face dimming on back-facing walls, while still
 *       responding to time-of-day sun direction. Intra-feature-mixed
 *       nodes' white-pixel sentinel triangles keep their real geometric
 *       normal so they pick up proper per-face PBR shading.</li>
 *   <li>{@link DracoBuffer#colored()} — no NORMAL (X3DMaterial unlit path).</li>
 *   <li>{@link DracoBuffer#coloredShaded()} — NORMAL <i>and</i> COLOR present
 *       (X3DMaterial / per-feature-type styling shaded path; selected when
 *       {@code --enable-shading} is on).</li>
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

    public static GeometryDefinition untexturedNoNormal() {
        GeometryDefinition def = new GeometryDefinition();
        def.geometryBuffers = List.of(DracoBuffer.untexturedNoNormal());
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

    public static GeometryDefinition texturedShaded() {
        GeometryDefinition def = new GeometryDefinition();
        def.geometryBuffers = List.of(DracoBuffer.texturedShaded());
        return def;
    }

    public static GeometryDefinition texturedColoredShaded() {
        GeometryDefinition def = new GeometryDefinition();
        def.geometryBuffers = List.of(DracoBuffer.texturedColoredShaded());
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

        public static DracoBuffer untexturedNoNormal() {
            return wrap(List.of("position", "feature-index"));
        }

        public static DracoBuffer textured() {
            return wrap(List.of("position", "uv0", "feature-index"));
        }

        public static DracoBuffer texturedColored() {
            return wrap(List.of("position", "uv0", "color", "feature-index"));
        }

        public static DracoBuffer texturedShaded() {
            return wrap(List.of("position", "normal", "uv0", "feature-index"));
        }

        public static DracoBuffer texturedColoredShaded() {
            return wrap(List.of("position", "normal", "uv0", "color", "feature-index"));
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
