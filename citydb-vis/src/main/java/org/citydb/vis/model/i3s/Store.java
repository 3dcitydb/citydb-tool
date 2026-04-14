/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.model.i3s;

import com.alibaba.fastjson2.annotation.JSONType;

import java.util.List;
import java.util.UUID;

@JSONType(alphabetic = false)
public class Store {
    private static final String EPSG_4326_URI = "http://www.opengis.net/def/crs/EPSG/0/4326";

    private String id;
    private String profile;
    private String version;
    private List<String> resourcePattern;
    private String rootNode;
    private double[] extent;
    private String indexCRS;
    private String vertexCRS;
    private String normalReferenceFrame;
    private List<String> textureEncoding;
    private String lodType;
    private String lodModel;
    private GeometrySchema defaultGeometrySchema;
    private String geometryEncoding;

    public static Store of(SceneLayer sceneLayer, boolean hasTextures) {
        Store store = new Store();
        store.id = UUID.randomUUID().toString();
        store.profile = "meshpyramids";
        store.version = SceneLayer.I3S_VERSION;
        store.resourcePattern = List.of(
                "3dNodeIndexDocument", "Geometry", "Attributes");
        // ArcGIS expects "./nodes/root"; CesiumJS works with either since
        // it uses node pages to locate nodes rather than this legacy URL.
        store.rootNode = "./nodes/root";

        double[] layerExtent = sceneLayer.getExtent();
        if (layerExtent != null) {
            store.extent = new double[]{
                    layerExtent[0], layerExtent[1], layerExtent[3], layerExtent[4]};
        }

        store.indexCRS = EPSG_4326_URI;
        store.vertexCRS = EPSG_4326_URI;
        store.normalReferenceFrame = "earth-centered";
        if (hasTextures) {
            store.textureEncoding = List.of("image/jpeg");
        }
        store.lodType = "MeshPyramid";
        store.lodModel = "node-switching";
        store.defaultGeometrySchema = GeometrySchema.full();
        store.geometryEncoding = "application/octet-stream";
        return store;
    }

    @JSONType(alphabetic = false)
    public static class GeometrySchema {
        private String geometryType;
        private List<HeaderEntry> header;
        private String topology;
        private List<String> ordering;
        private VertexAttributes vertexAttributes;
        private List<String> featureAttributeOrder;
        private FeatureAttributes featureAttributes;

        public static GeometrySchema full() {
            GeometrySchema schema = new GeometrySchema();
            schema.geometryType = "triangles";
            schema.header = List.of(
                    new HeaderEntry("vertexCount", "UInt32"),
                    new HeaderEntry("featureCount", "UInt32"));
            schema.topology = "PerAttributeArray";
            // Global schema must match what is actually written to buffer 0
            // for every node, regardless of per-node texture state. The
            // encoder pads uv0 with zeros and color with opaque-white when
            // a node is untextured so the binary layout stays constant.
            schema.ordering = List.of("position", "normal", "uv0", "color");
            schema.vertexAttributes = VertexAttributes.full();
            schema.featureAttributeOrder = List.of("id", "faceRange");
            schema.featureAttributes = FeatureAttributes.defaults();
            return schema;
        }
    }

    @JSONType(alphabetic = false)
    public record HeaderEntry(String property, String type) {
    }

    @JSONType(alphabetic = false)
    public record AttributeDef(String valueType, int valuesPerElement) {
    }

    @JSONType(alphabetic = false)
    public static class VertexAttributes {
        private AttributeDef position;
        private AttributeDef normal;
        private AttributeDef uv0;
        private AttributeDef color;

        public static VertexAttributes full() {
            VertexAttributes attrs = new VertexAttributes();
            attrs.position = new AttributeDef("Float32", 3);
            attrs.normal = new AttributeDef("Float32", 3);
            attrs.uv0 = new AttributeDef("Float32", 2);
            attrs.color = new AttributeDef("UInt8", 4);
            return attrs;
        }
    }

    @JSONType(alphabetic = false)
    public static class FeatureAttributes {
        private AttributeDef id;
        private AttributeDef faceRange;

        public static FeatureAttributes defaults() {
            FeatureAttributes attrs = new FeatureAttributes();
            attrs.id = new AttributeDef("UInt64", 1);
            attrs.faceRange = new AttributeDef("UInt32", 2);
            return attrs;
        }
    }
}
