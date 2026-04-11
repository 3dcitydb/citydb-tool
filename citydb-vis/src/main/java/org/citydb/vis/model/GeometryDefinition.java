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
     * Untextured variant: declare only the Draco buffer at index 0.
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
     * Textured variant: dual buffer (raw=0, Draco=1), matching the NYC reference layout.
     * CesiumJS finds "uv0" in the Draco attributes and selects it via the
     * normal path — the fallback bug does not apply.
     */
    public static GeometryDefinition textured() {
        GeometryDefinition def = new GeometryDefinition();
        def.geometryBuffers = List.of(RawBuffer.textured(), DracoBuffer.of(true));
        return def;
    }

    @JSONType(alphabetic = false)
    public static class RawBuffer {
        private int offset;
        private BufferAttribute position;
        private BufferAttribute normal;
        private BufferAttribute uv0;
        private BufferAttribute featureId;
        private BufferAttribute faceRange;

        public static RawBuffer textured() {
            RawBuffer buffer = new RawBuffer();
            buffer.offset = 8;
            buffer.position = BufferAttribute.of("Float32", 3);
            buffer.normal = BufferAttribute.of("Float32", 3);
            buffer.uv0 = BufferAttribute.of("Float32", 2);
            buffer.featureId = BufferAttribute.perFeature("UInt64", 1);
            buffer.faceRange = BufferAttribute.perFeature("UInt32", 2);
            return buffer;
        }
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

    @JSONType(alphabetic = false)
    public static class BufferAttribute {
        private String type;
        private int component;
        private String binding;

        public static BufferAttribute of(String type, int component) {
            BufferAttribute attr = new BufferAttribute();
            attr.type = type;
            attr.component = component;
            return attr;
        }

        public static BufferAttribute perFeature(String type, int component) {
            BufferAttribute attr = of(type, component);
            attr.binding = "per-feature";
            return attr;
        }
    }
}
