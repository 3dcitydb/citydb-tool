/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.writer;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import org.citydb.vis.scene.I3SNode;
import org.citydb.vis.scene.SceneLayer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Writes all JSON files for an I3S scene layer: the scene layer descriptor,
 * node pages, per-node feature data, and shared resources.
 */
class I3SJsonSerializer {
    private static final int NODES_PER_PAGE = 64;

    void writeSceneLayerJson(Path layerDir, SceneLayer sceneLayer,
                             List<I3SAttributeEncoder.AttrField> attrFields,
                             boolean hasTextures) throws IOException {
        JSONObject root = new JSONObject();
        root.put("id", 0);
        root.put("version", SceneLayer.I3S_VERSION);
        root.put("name", sceneLayer.getName());
        root.put("description", sceneLayer.getDescription());
        root.put("layerType", SceneLayer.LAYER_TYPE);

        root.put("heightModelInfo", new JSONObject()
                .fluentPut("heightModel", "gravity_related_height")
                .fluentPut("vertCRS", "EGM96_Geoid")
                .fluentPut("heightUnit", "meter"));

        root.put("spatialReference", new JSONObject()
                .fluentPut("wkid", sceneLayer.getWkid())
                .fluentPut("latestWkid", sceneLayer.getWkid()));

        // Store
        JSONObject store = new JSONObject();
        store.put("id", UUID.randomUUID().toString());
        store.put("profile", "meshpyramids");
        store.put("version", SceneLayer.I3S_VERSION);
        store.put("resourcePattern", JSONArray.of(
                "3dNodeIndexDocument", "SharedResource", "Geometry", "Attributes"));
        store.put("rootNode", "./nodes/0");

        double[] storeExtent = sceneLayer.getExtent();
        if (storeExtent != null) {
            store.put("extent", JSONArray.of(
                    storeExtent[0], storeExtent[1], storeExtent[3], storeExtent[4]));
        }

        store.put("indexCRS", "http://www.opengis.net/def/crs/EPSG/0/4326");
        store.put("vertexCRS", "http://www.opengis.net/def/crs/EPSG/0/4326");
        store.put("normalReferenceFrame", "earth-centered");
        if (hasTextures) {
            store.put("textureEncoding", JSONArray.of("image/jpeg"));
        }
        store.put("lodType", "MeshPyramid");
        store.put("lodModel", "node-switching");

        // defaultGeometrySchema
        JSONObject geoSchema = new JSONObject();
        geoSchema.put("geometryType", "triangles");
        geoSchema.put("header", JSONArray.of(
                new JSONObject().fluentPut("property", "vertexCount").fluentPut("type", "UInt32"),
                new JSONObject().fluentPut("property", "featureCount").fluentPut("type", "UInt32")));
        geoSchema.put("topology", "PerAttributeArray");
        geoSchema.put("ordering", hasTextures
                ? JSONArray.of("position", "normal", "uv0")
                : JSONArray.of("position", "normal"));

        JSONObject vertexAttrs = new JSONObject();
        vertexAttrs.put("position", new JSONObject()
                .fluentPut("valueType", "Float32").fluentPut("valuesPerElement", 3));
        vertexAttrs.put("normal", new JSONObject()
                .fluentPut("valueType", "Float32").fluentPut("valuesPerElement", 3));
        if (hasTextures) {
            vertexAttrs.put("uv0", new JSONObject()
                    .fluentPut("valueType", "Float32").fluentPut("valuesPerElement", 2));
        }
        geoSchema.put("vertexAttributes", vertexAttrs);

        geoSchema.put("featureAttributeOrder", JSONArray.of("id", "faceRange"));
        geoSchema.put("featureAttributes", new JSONObject()
                .fluentPut("id", new JSONObject()
                        .fluentPut("valueType", "UInt64").fluentPut("valuesPerElement", 1))
                .fluentPut("faceRange", new JSONObject()
                        .fluentPut("valueType", "UInt32").fluentPut("valuesPerElement", 2)));

        store.put("defaultGeometrySchema", geoSchema);
        store.put("geometryEncoding", "application/octet-stream");
        root.put("store", store);

        // Geometry definitions
        JSONArray geoDefs = new JSONArray();
        // Definition 0: dual buffer (raw + Draco), no uv0
        geoDefs.add(buildGeometryDefinition(false));
        if (hasTextures) {
            // Definition 1: dual buffer (raw + Draco) with uv0
            geoDefs.add(buildGeometryDefinition(true));
        }
        root.put("geometryDefinitions", geoDefs);

        // Texture set definitions
        if (hasTextures) {
            root.put("textureSetDefinitions", JSONArray.of(
                    new JSONObject().fluentPut("formats", JSONArray.of(
                            new JSONObject().fluentPut("name", "0").fluentPut("format", "jpg")))));
        }

        // Material definitions
        JSONObject untexturedMat = new JSONObject()
                .fluentPut("cullFace", "back")
                .fluentPut("pbrMetallicRoughness", new JSONObject()
                        .fluentPut("metallicFactor", 0));
        if (hasTextures) {
            JSONObject texturedMat = new JSONObject()
                    .fluentPut("cullFace", "back")
                    .fluentPut("pbrMetallicRoughness", new JSONObject()
                            .fluentPut("baseColorTexture", new JSONObject()
                                    .fluentPut("textureSetDefinitionId", 0)
                                    .fluentPut("texCoord", 0))
                            .fluentPut("metallicFactor", 0));
            root.put("materialDefinitions", JSONArray.of(untexturedMat, texturedMat));
        } else {
            root.put("materialDefinitions", JSONArray.of(untexturedMat));
        }

        // fullExtent
        double[] extent = sceneLayer.getExtent();
        if (extent != null) {
            root.put("fullExtent", new JSONObject()
                    .fluentPut("xmin", extent[0])
                    .fluentPut("ymin", extent[1])
                    .fluentPut("xmax", extent[3])
                    .fluentPut("ymax", extent[4])
                    .fluentPut("zmin", extent[2])
                    .fluentPut("zmax", extent[5]));
        }

        // fields
        JSONArray fields = new JSONArray();
        for (I3SAttributeEncoder.AttrField field : attrFields) {
            String esriType = switch (field.type()) {
                case INT -> "esriFieldTypeInteger";
                case DOUBLE -> "esriFieldTypeDouble";
                case STRING -> "esriFieldTypeString";
            };
            fields.add(new JSONObject()
                    .fluentPut("name", field.name())
                    .fluentPut("type", esriType)
                    .fluentPut("alias", field.name()));
        }
        root.put("fields", fields);

        // attributeStorageInfo
        JSONArray storageInfo = new JSONArray();
        for (int i = 0; i < attrFields.size(); i++) {
            I3SAttributeEncoder.AttrField field = attrFields.get(i);
            JSONObject info = new JSONObject();
            info.put("key", "f_" + i);
            info.put("name", field.name());
            switch (field.type()) {
                case INT -> {
                    info.put("header", JSONArray.of(
                            new JSONObject().fluentPut("property", "count").fluentPut("valueType", "UInt32")));
                    info.put("ordering", JSONArray.of("attributeValues"));
                    info.put("attributeValues", new JSONObject()
                            .fluentPut("valueType", "Int32").fluentPut("valuesPerElement", 1));
                }
                case DOUBLE -> {
                    info.put("header", JSONArray.of(
                            new JSONObject().fluentPut("property", "count").fluentPut("valueType", "UInt32")));
                    info.put("ordering", JSONArray.of("attributeValues"));
                    info.put("attributeValues", new JSONObject()
                            .fluentPut("valueType", "Float64").fluentPut("valuesPerElement", 1));
                }
                case STRING -> {
                    info.put("header", JSONArray.of(
                            new JSONObject().fluentPut("property", "count").fluentPut("valueType", "UInt32"),
                            new JSONObject().fluentPut("property", "attributeValuesByteCount").fluentPut("valueType", "UInt32")));
                    info.put("ordering", JSONArray.of("attributeByteCounts", "attributeValues"));
                    info.put("attributeByteCounts", new JSONObject()
                            .fluentPut("valueType", "UInt32").fluentPut("valuesPerElement", 1));
                    info.put("attributeValues", new JSONObject()
                            .fluentPut("valueType", "String").fluentPut("valuesPerElement", 1).fluentPut("encoding", "UTF-8"));
                }
            }
            storageInfo.add(info);
        }
        root.put("attributeStorageInfo", storageInfo);

        // nodePages
        root.put("nodePages", new JSONObject()
                .fluentPut("nodesPerPage", NODES_PER_PAGE)
                .fluentPut("lodSelectionMetricType", "maxScreenThresholdSQ"));

        writeJson(layerDir.resolve("index.json"), root);
    }

    private static JSONObject buildGeometryDefinition(boolean withUV) {
        JSONArray dracoAttrs = withUV
                ? JSONArray.of("position", "uv0", "feature-index")
                : JSONArray.of("position", "normal", "feature-index");
        JSONObject dracoBuffer = new JSONObject()
                .fluentPut("compressedAttributes", new JSONObject()
                        .fluentPut("encoding", "draco")
                        .fluentPut("attributes", dracoAttrs));

        if (!withUV) {
            // Untextured: declare only the Draco buffer at index 0.
            // CesiumJS _findBestGeometryBuffers() requires ["position","uv0"];
            // without "uv0" it falls back to bufferIndex=0, so the Draco buffer
            // must be at index 0 for the fallback to load the correct data.
            return new JSONObject()
                    .fluentPut("geometryBuffers", JSONArray.of(dracoBuffer));
        }

        // Textured: dual buffer (raw=0, Draco=1), matching the NYC reference layout.
        // CesiumJS finds "uv0" in the Draco attributes and selects it via the
        // normal path — the fallback bug does not apply.
        JSONObject rawBuffer = new JSONObject();
        rawBuffer.put("offset", 8);
        rawBuffer.put("position", new JSONObject().fluentPut("type", "Float32").fluentPut("component", 3));
        rawBuffer.put("normal", new JSONObject().fluentPut("type", "Float32").fluentPut("component", 3));
        rawBuffer.put("uv0", new JSONObject().fluentPut("type", "Float32").fluentPut("component", 2));
        rawBuffer.put("featureId", new JSONObject()
                .fluentPut("type", "UInt64").fluentPut("component", 1).fluentPut("binding", "per-feature"));
        rawBuffer.put("faceRange", new JSONObject()
                .fluentPut("type", "UInt32").fluentPut("component", 2).fluentPut("binding", "per-feature"));

        return new JSONObject()
                .fluentPut("geometryBuffers", JSONArray.of(rawBuffer, dracoBuffer));
    }

    /**
     * Write node pages. Uses a {@code Set<Integer>} to determine which nodes
     * have geometry (instead of the full nodeFeatureMap).
     */
    void writeNodePages(Path layerDir, List<I3SNode> nodes,
                        Set<Integer> meshNodeIndices,
                        boolean hasTextures) throws IOException {
        int pageCount = (nodes.size() + NODES_PER_PAGE - 1) / NODES_PER_PAGE;

        for (int page = 0; page < pageCount; page++) {
            JSONArray nodesArray = new JSONArray();

            int start = page * NODES_PER_PAGE;
            int end = Math.min(start + NODES_PER_PAGE, nodes.size());

            for (int i = start; i < end; i++) {
                I3SNode node = nodes.get(i);
                JSONObject nodeObj = new JSONObject();
                nodeObj.put("index", node.getIndex());

                if (node.getMbs() != null) {
                    double[] mbs = node.getMbs().toMbs();
                    nodeObj.put("mbs", JSONArray.of(mbs[0], mbs[1], mbs[2], mbs[3]));
                }

                nodeObj.put("lodThreshold", node.getLodThreshold());

                JSONArray childIndices = new JSONArray();
                for (I3SNode child : node.getChildren()) {
                    childIndices.add(child.getIndex());
                }
                nodeObj.put("children", childIndices);

                nodeObj.put("parentIndex", node.getParent() != null
                        ? node.getParent().getIndex() : -1);

                boolean hasMesh = meshNodeIndices.contains(node.getIndex());
                if (hasMesh) {
                    int vtxCount = node.getOutputVertexCount();
                    int ftCount = node.getFeatureCount();
                    boolean nodeHasTexture = hasTextures && node.hasTexture();
                    int matDef = nodeHasTexture ? 1 : 0;
                    int matRes = nodeHasTexture ? node.getIndex() : -1;
                    int geoDef = nodeHasTexture ? 1 : 0;

                    nodeObj.put("mesh", new JSONObject()
                            .fluentPut("material", new JSONObject()
                                    .fluentPut("definition", matDef)
                                    .fluentPut("resource", matRes))
                            .fluentPut("geometry", new JSONObject()
                                    .fluentPut("definition", geoDef)
                                    .fluentPut("resource", node.getIndex())
                                    .fluentPut("vertexCount", vtxCount)
                                    .fluentPut("featureCount", ftCount))
                            .fluentPut("attribute", new JSONObject()
                                    .fluentPut("resource", node.getIndex())));
                } else {
                    nodeObj.put("mesh", null);
                }

                nodeObj.put("featureCount", node.getFeatureCount());
                nodesArray.add(nodeObj);
            }

            JSONObject pageObj = new JSONObject().fluentPut("nodes", nodesArray);
            Path nodePageDir = layerDir.resolve("nodepages").resolve(String.valueOf(page));
            Files.createDirectories(nodePageDir);
            writeJson(nodePageDir.resolve("index.json"), pageObj);
        }
    }

    /**
     * Write per-node shared resource (legacy material/texture definitions).
     * CesiumJS reads this for texture metadata.
     */
    void writeSharedResource(Path layerDir, I3SNode node, boolean isAtlas) throws IOException {
        JSONObject root = new JSONObject();

        // Material definition (legacy format for CesiumJS compatibility)
        root.put("materialDefinitions", new JSONObject()
                .fluentPut("Mat0", new JSONObject()
                        .fluentPut("type", "standard")
                        .fluentPut("name", "standard")
                        .fluentPut("params", new JSONObject()
                                .fluentPut("vertexColors", false)
                                .fluentPut("reflectivity", 0)
                                .fluentPut("ambient", JSONArray.of(0, 0, 0))
                                .fluentPut("diffuse", JSONArray.of(1, 1, 1))
                                .fluentPut("specular", JSONArray.of(0.09803921568, 0.09803921568, 0.09803921568))
                                .fluentPut("shininess", 1)
                                .fluentPut("renderMode", "solid")
                                .fluentPut("cullFace", "back"))));

        // Texture definition
        root.put("textureDefinitions", new JSONObject()
                .fluentPut("0", new JSONObject()
                        .fluentPut("encoding", JSONArray.of("image/jpeg"))
                        .fluentPut("wrap", JSONArray.of("none", "none"))
                        .fluentPut("atlas", isAtlas)
                        .fluentPut("uvSet", "uv0")
                        .fluentPut("channels", "rgb")
                        .fluentPut("images", JSONArray.of(new JSONObject()
                                .fluentPut("id", "0")
                                .fluentPut("size", 512)
                                .fluentPut("pixelInWorldUnits", 0)
                                .fluentPut("href", JSONArray.of("../textures/0"))))));

        Path sharedDir = layerDir.resolve("nodes").resolve(String.valueOf(node.getIndex()))
                .resolve("shared");
        Files.createDirectories(sharedDir);
        writeJson(sharedDir.resolve("index.json"), root);
    }

    /**
     * Write per-node feature metadata to features/0/index.json.
     */
    void writeNodeFeatures(Path layerDir, I3SNode node,
                           List<FeatureData> features) throws IOException {
        JSONArray array = new JSONArray();
        if (features != null) {
            for (FeatureData fd : features) {
                array.add(new JSONObject()
                        .fluentPut("id", fd.id())
                        .fluentPut("objectId", fd.objectId())
                        .fluentPut("featureType", fd.featureType()));
            }
        }

        Files.writeString(layerDir.resolve("nodes").resolve(String.valueOf(node.getIndex()))
                .resolve("features").resolve("0").resolve("index.json"),
                array.toJSONString());
    }

    private static void writeJson(Path file, JSONObject json) throws IOException {
        Files.writeString(file, json.toJSONString(JSONWriter.Feature.PrettyFormat));
    }
}
