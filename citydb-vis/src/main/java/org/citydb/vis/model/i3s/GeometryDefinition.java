/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Geometry definition with two geometryBuffers, mirroring Esri's NYC
 * reference layout:
 * <ul>
 *   <li>Buffer 0: uncompressed SoA — position (Float32×3), normal
 *       (Float32×3), uv0 (Float32×2), color (UInt8×4) per-vertex;
 *       featureId (UInt64) + faceRange (UInt32×2) per-feature. Used by
 *       ArcGIS Pro for single-feature picking.</li>
 *   <li>Buffer 1: Draco-compressed — position + (normal or uv0) +
 *       feature-index. Used by CesiumJS and by ArcGIS Pro for rendering.</li>
 * </ul>
 */
@JSONType(alphabetic = false)
public class GeometryDefinition {
    private List<Object> geometryBuffers;

    public static GeometryDefinition untextured() {
        GeometryDefinition def = new GeometryDefinition();
        def.geometryBuffers = List.of(
                UncompressedBuffer.fullLayout(),
                DracoBuffer.of(false));
        return def;
    }

    public static GeometryDefinition textured() {
        GeometryDefinition def = new GeometryDefinition();
        def.geometryBuffers = List.of(
                UncompressedBuffer.fullLayout(),
                DracoBuffer.of(true));
        return def;
    }

    /**
     * Raw uncompressed buffer. Emits JSON in insertion order so the resulting
     * field order matches the binary SoA layout expected by I3S readers:
     * {@code offset, position, normal, uv0, color, featureId, faceRange}.
     * Always declares the full per-vertex attribute set to stay consistent
     * with {@code defaultGeometrySchema}; the encoder pads uv0 with zeros and
     * color with opaque-white for untextured nodes.
     */
    @JSONType(alphabetic = false)
    public static class UncompressedBuffer extends LinkedHashMap<String, Object> {
        public static UncompressedBuffer fullLayout() {
            UncompressedBuffer buf = new UncompressedBuffer();
            buf.put("offset", 8);
            buf.put("position", Map.of("type", "Float32", "component", 3));
            buf.put("normal", Map.of("type", "Float32", "component", 3));
            buf.put("uv0", Map.of("type", "Float32", "component", 2));
            buf.put("color", Map.of("type", "UInt8", "component", 4));
            buf.put("featureId", Map.of(
                    "type", "UInt64",
                    "component", 1,
                    "binding", "per-feature"));
            buf.put("faceRange", Map.of(
                    "type", "UInt32",
                    "component", 2,
                    "binding", "per-feature"));
            return buf;
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
}
