/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model;

import com.alibaba.fastjson2.annotation.JSONType;

import java.util.List;

@JSONType(alphabetic = false)
public class GeometryDefinition {
    private List<Object> geometryBuffers;

    /**
     * Untextured variant: Draco buffer only at index 0.
     * CesiumJS _findBestGeometryBuffers() requires ["position","uv0"];
     * without "uv0" it falls back to bufferIndex=0, so the Draco buffer
     * must be at index 0 for the fallback to load the correct data.
     */
    public static GeometryDefinition untextured() {
        GeometryDefinition def = new GeometryDefinition();
        def.geometryBuffers = List.of(DracoBuffer.of(false));
        return def;
    }

    /**
     * Textured variant: Draco buffer only at index 0.
     * CesiumJS _findBestGeometryBuffers() finds "uv0" in the Draco
     * compressedAttributes and selects this buffer via the normal path.
     */
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
