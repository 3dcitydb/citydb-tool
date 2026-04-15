/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;

import java.util.List;

/**
 * Geometry definition with a single Draco-compressed geometryBuffer.
 * Attributes: {@code position + (normal or uv0) + feature-index}. The
 * feature-index attribute carries per-vertex feature identification for
 * picking (via injected {@code i3s-feature-ids} Draco metadata).
 */
@JSONType(alphabetic = false)
public class GeometryDefinition {
    private List<Object> geometryBuffers;

    public static GeometryDefinition untextured() {
        GeometryDefinition def = new GeometryDefinition();
        def.geometryBuffers = List.of(DracoBuffer.of(false));
        return def;
    }

    public static GeometryDefinition textured() {
        GeometryDefinition def = new GeometryDefinition();
        def.geometryBuffers = List.of(DracoBuffer.of(true));
        return def;
    }

    public static class DracoBuffer {
        private CompressedAttributes compressedAttributes;

        public static DracoBuffer of(boolean withUV) {
            List<String> attrs = withUV
                    ? List.of("position", "uv0", "feature-index")
                    : List.of("position", "normal", "feature-index");
            DracoBuffer buffer = new DracoBuffer();
            buffer.compressedAttributes = new CompressedAttributes("draco", attrs);
            return buffer;
        }
    }

    @JSONType(alphabetic = false)
    public record CompressedAttributes(String encoding, List<String> attributes) {
    }
}
