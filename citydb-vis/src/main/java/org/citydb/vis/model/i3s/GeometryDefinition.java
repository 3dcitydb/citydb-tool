/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;

import java.util.List;

/**
 * Geometry definition with a single Draco-compressed geometryBuffer. The
 * four layouts cover the cross-product of textured/untextured and
 * with/without baked X3DMaterial vertex colors. The feature-index attribute
 * carries per-vertex feature identification for picking (via injected
 * {@code i3s-feature-ids} Draco metadata).
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
