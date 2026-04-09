/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.vis.writer;

import org.citydb.vis.scene.I3SNode;
import org.citydb.vis.scene.SceneLayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"id\": 0,\n");
        json.append("  \"version\": \"").append(SceneLayer.I3S_VERSION).append("\",\n");
        json.append("  \"name\": \"").append(escapeJson(sceneLayer.getName())).append("\",\n");
        json.append("  \"description\": \"").append(escapeJson(sceneLayer.getDescription())).append("\",\n");
        json.append("  \"layerType\": \"").append(SceneLayer.LAYER_TYPE).append("\",\n");

        json.append("  \"heightModelInfo\": {\n");
        json.append("    \"heightModel\": \"gravity_related_height\",\n");
        json.append("    \"vertCRS\": \"EGM96_Geoid\",\n");
        json.append("    \"heightUnit\": \"meter\"\n");
        json.append("  },\n");

        json.append("  \"spatialReference\": {\n");
        json.append("    \"wkid\": ").append(sceneLayer.getWkid()).append(",\n");
        json.append("    \"latestWkid\": ").append(sceneLayer.getWkid()).append("\n");
        json.append("  },\n");

        json.append("  \"store\": {\n");
        json.append("    \"id\": \"").append(UUID.randomUUID()).append("\",\n");
        json.append("    \"profile\": \"meshpyramids\",\n");
        json.append("    \"version\": \"").append(SceneLayer.I3S_VERSION).append("\",\n");
        json.append("    \"resourcePattern\": [\"3dNodeIndexDocument\", \"SharedResource\", \"Geometry\", \"Attributes\"],\n");
        json.append("    \"rootNode\": \"./nodes/0\",\n");
        double[] storeExtent = sceneLayer.getExtent();
        if (storeExtent != null) {
            json.append("    \"extent\": [").append(storeExtent[0]).append(", ")
                    .append(storeExtent[1]).append(", ")
                    .append(storeExtent[3]).append(", ")
                    .append(storeExtent[4]).append("],\n");
        }
        json.append("    \"indexCRS\": \"http://www.opengis.net/def/crs/EPSG/0/4326\",\n");
        json.append("    \"vertexCRS\": \"http://www.opengis.net/def/crs/EPSG/0/4326\",\n");
        json.append("    \"normalReferenceFrame\": \"earth-centered\",\n");
        if (hasTextures) {
            json.append("    \"textureEncoding\": [\"image/jpeg\"],\n");
        }
        json.append("    \"lodType\": \"MeshPyramid\",\n");
        json.append("    \"lodModel\": \"node-switching\",\n");
        json.append("    \"defaultGeometrySchema\": {\n");
        json.append("      \"geometryType\": \"triangles\",\n");
        json.append("      \"header\": [\n");
        json.append("        {\"property\": \"vertexCount\", \"type\": \"UInt32\"},\n");
        json.append("        {\"property\": \"featureCount\", \"type\": \"UInt32\"}\n");
        json.append("      ],\n");
        json.append("      \"topology\": \"PerAttributeArray\",\n");
        if (hasTextures) {
            json.append("      \"ordering\": [\"position\", \"normal\", \"uv0\"],\n");
        } else {
            json.append("      \"ordering\": [\"position\", \"normal\"],\n");
        }
        json.append("      \"vertexAttributes\": {\n");
        json.append("        \"position\": {\"valueType\": \"Float32\", \"valuesPerElement\": 3},\n");
        if (hasTextures) {
            json.append("        \"normal\": {\"valueType\": \"Float32\", \"valuesPerElement\": 3},\n");
            json.append("        \"uv0\": {\"valueType\": \"Float32\", \"valuesPerElement\": 2}\n");
        } else {
            json.append("        \"normal\": {\"valueType\": \"Float32\", \"valuesPerElement\": 3}\n");
        }
        json.append("      },\n");
        json.append("      \"featureAttributeOrder\": [\"id\", \"faceRange\"],\n");
        json.append("      \"featureAttributes\": {\n");
        json.append("        \"id\": {\"valueType\": \"UInt64\", \"valuesPerElement\": 1},\n");
        json.append("        \"faceRange\": {\"valueType\": \"UInt32\", \"valuesPerElement\": 2}\n");
        json.append("      }\n");
        json.append("    },\n");
        json.append("    \"geometryEncoding\": \"application/octet-stream\"\n");
        json.append("  },\n");

        // Geometry definitions
        // Definition 0: dual buffer, no uv0 (for untextured nodes)
        // Definition 1: dual buffer with uv0 (for textured nodes, only when hasTextures)
        json.append("  \"geometryDefinitions\": [\n");
        // Definition 0: dual buffer (raw + Draco), no uv0
        json.append("    {\n");
        json.append("      \"geometryBuffers\": [\n");
        json.append("        {\n");
        json.append("          \"offset\": 8,\n");
        json.append("          \"position\": {\"type\": \"Float32\", \"component\": 3},\n");
        json.append("          \"normal\": {\"type\": \"Float32\", \"component\": 3},\n");
        json.append("          \"featureId\": {\"type\": \"UInt64\", \"component\": 1, \"binding\": \"per-feature\"},\n");
        json.append("          \"faceRange\": {\"type\": \"UInt32\", \"component\": 2, \"binding\": \"per-feature\"}\n");
        json.append("        },\n");
        json.append("        {\n");
        json.append("          \"compressedAttributes\": {\n");
        json.append("            \"encoding\": \"draco\",\n");
        json.append("            \"attributes\": [\"position\", \"normal\", \"feature-index\"]\n");
        json.append("          }\n");
        json.append("        }\n");
        json.append("      ]\n");
        if (hasTextures) {
            json.append("    },\n");
            // Definition 1: dual buffer (raw + Draco) with uv0
            json.append("    {\n");
            json.append("      \"geometryBuffers\": [\n");
            json.append("        {\n");
            json.append("          \"offset\": 8,\n");
            json.append("          \"position\": {\"type\": \"Float32\", \"component\": 3},\n");
            json.append("          \"normal\": {\"type\": \"Float32\", \"component\": 3},\n");
            json.append("          \"uv0\": {\"type\": \"Float32\", \"component\": 2},\n");
            json.append("          \"featureId\": {\"type\": \"UInt64\", \"component\": 1, \"binding\": \"per-feature\"},\n");
            json.append("          \"faceRange\": {\"type\": \"UInt32\", \"component\": 2, \"binding\": \"per-feature\"}\n");
            json.append("        },\n");
            json.append("        {\n");
            json.append("          \"compressedAttributes\": {\n");
            json.append("            \"encoding\": \"draco\",\n");
            json.append("            \"attributes\": [\"position\", \"uv0\", \"feature-index\"]\n");
            json.append("          }\n");
            json.append("        }\n");
            json.append("      ]\n");
        }
        json.append("    }\n");
        json.append("  ],\n");

        // Texture set definitions (only when textures present)
        if (hasTextures) {
            json.append("  \"textureSetDefinitions\": [{\n");
            json.append("    \"formats\": [{\"name\": \"0\", \"format\": \"jpg\"}]\n");
            json.append("  }],\n");
        }

        // Material definitions (matching SF reference format)
        if (hasTextures) {
            // materialDef 0: no texture (for untextured nodes)
            // materialDef 1: with texture (PBR baseColorTexture)
            json.append("  \"materialDefinitions\": [\n");
            json.append("    {\"cullFace\": \"back\", \"pbrMetallicRoughness\": {\"metallicFactor\": 0}},\n");
            json.append("    {\n");
            json.append("      \"cullFace\": \"back\",\n");
            json.append("      \"pbrMetallicRoughness\": {\n");
            json.append("        \"baseColorTexture\": {\"textureSetDefinitionId\": 0, \"texCoord\": 0},\n");
            json.append("        \"metallicFactor\": 0\n");
            json.append("      }\n");
            json.append("    }\n");
            json.append("  ],\n");
        } else {
            json.append("  \"materialDefinitions\": [{\"cullFace\": \"back\", \"pbrMetallicRoughness\": {\"metallicFactor\": 0}}],\n");
        }

        // Use the axis-aligned bounding box directly for fullExtent
        double[] extent = sceneLayer.getExtent();
        if (extent != null) {
            json.append("  \"fullExtent\": {\n");
            json.append("    \"xmin\": ").append(extent[0]).append(",\n");
            json.append("    \"ymin\": ").append(extent[1]).append(",\n");
            json.append("    \"xmax\": ").append(extent[3]).append(",\n");
            json.append("    \"ymax\": ").append(extent[4]).append(",\n");
            json.append("    \"zmin\": ").append(extent[2]).append(",\n");
            json.append("    \"zmax\": ").append(extent[5]).append("\n");
            json.append("  },\n");
        }

        // Attribute fields for feature picking
        json.append("  \"fields\": [\n");
        for (int i = 0; i < attrFields.size(); i++) {
            I3SAttributeEncoder.AttrField field = attrFields.get(i);
            if (i > 0) json.append(",\n");
            String esriType = switch (field.type()) {
                case INT -> "esriFieldTypeInteger";
                case DOUBLE -> "esriFieldTypeDouble";
                case STRING -> "esriFieldTypeString";
            };
            json.append("    {\"name\": \"").append(escapeJson(field.name()))
                    .append("\", \"type\": \"").append(esriType)
                    .append("\", \"alias\": \"").append(escapeJson(field.name())).append("\"}");
        }
        json.append("\n  ],\n");
        json.append("  \"attributeStorageInfo\": [\n");
        for (int i = 0; i < attrFields.size(); i++) {
            I3SAttributeEncoder.AttrField field = attrFields.get(i);
            if (i > 0) json.append(",\n");
            json.append("    {\n");
            json.append("      \"key\": \"f_").append(i).append("\",\n");
            json.append("      \"name\": \"").append(escapeJson(field.name())).append("\",\n");
            switch (field.type()) {
                case INT -> {
                    json.append("      \"header\": [{\"property\": \"count\", \"valueType\": \"UInt32\"}],\n");
                    json.append("      \"ordering\": [\"attributeValues\"],\n");
                    json.append("      \"attributeValues\": {\"valueType\": \"Int32\", \"valuesPerElement\": 1}\n");
                }
                case DOUBLE -> {
                    json.append("      \"header\": [{\"property\": \"count\", \"valueType\": \"UInt32\"}],\n");
                    json.append("      \"ordering\": [\"attributeValues\"],\n");
                    json.append("      \"attributeValues\": {\"valueType\": \"Float64\", \"valuesPerElement\": 1}\n");
                }
                case STRING -> {
                    json.append("      \"header\": [\n");
                    json.append("        {\"property\": \"count\", \"valueType\": \"UInt32\"},\n");
                    json.append("        {\"property\": \"attributeValuesByteCount\", \"valueType\": \"UInt32\"}\n");
                    json.append("      ],\n");
                    json.append("      \"ordering\": [\"attributeByteCounts\", \"attributeValues\"],\n");
                    json.append("      \"attributeByteCounts\": {\"valueType\": \"UInt32\", \"valuesPerElement\": 1},\n");
                    json.append("      \"attributeValues\": {\"valueType\": \"String\", \"valuesPerElement\": 1, \"encoding\": \"UTF-8\"}\n");
                }
            }
            json.append("    }");
        }
        json.append("\n  ],\n");

        json.append("  \"nodePages\": {\n");
        json.append("    \"nodesPerPage\": ").append(NODES_PER_PAGE).append(",\n");
        json.append("    \"lodSelectionMetricType\": \"maxScreenThresholdSQ\"\n");
        json.append("  }\n");
        json.append("}\n");

        Files.write(layerDir.resolve("index.json"),
                json.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Write node pages. Uses a {@code Set<Integer>} to determine which nodes
     * have geometry (instead of the full nodeFeatureMap).
     */
    void writeNodePages(Path layerDir, List<I3SNode> nodes,
                        Set<Integer> meshNodeIndices,
                        boolean hasTextures) throws IOException {
        int pageSize = NODES_PER_PAGE;
        int pageCount = (nodes.size() + pageSize - 1) / pageSize;

        for (int page = 0; page < pageCount; page++) {
            StringBuilder json = new StringBuilder();
            json.append("{\n  \"nodes\": [\n");

            int start = page * pageSize;
            int end = Math.min(start + pageSize, nodes.size());

            for (int i = start; i < end; i++) {
                I3SNode node = nodes.get(i);
                if (i > start) json.append(",\n");

                json.append("    {\n");
                json.append("      \"index\": ").append(node.getIndex()).append(",\n");

                if (node.getMbs() != null) {
                    double[] mbs = node.getMbs().toMbs();
                    json.append("      \"mbs\": [").append(mbs[0]).append(", ")
                            .append(mbs[1]).append(", ").append(mbs[2]).append(", ")
                            .append(mbs[3]).append("],\n");
                }

                json.append("      \"lodThreshold\": ").append(node.getLodThreshold()).append(",\n");

                json.append("      \"children\": [");
                List<I3SNode> children = node.getChildren();
                for (int c = 0; c < children.size(); c++) {
                    if (c > 0) json.append(", ");
                    json.append(children.get(c).getIndex());
                }
                json.append("],\n");

                json.append("      \"parentIndex\": ").append(node.getParent() != null ?
                        node.getParent().getIndex() : -1).append(",\n");

                boolean hasMesh = meshNodeIndices.contains(node.getIndex());
                if (hasMesh) {
                    int vtxCount = node.getOutputVertexCount();
                    int ftCount = node.getFeatureCount();
                    boolean nodeHasTexture = hasTextures && node.hasTexture();

                    // material: definition 1 (textured PBR) or 0 (untextured)
                    // resource: node index (for texture file) or -1
                    // geometry: definition 0 (no uv0) or 1 (with uv0)
                    int matDef = nodeHasTexture ? 1 : 0;
                    int matRes = nodeHasTexture ? node.getIndex() : -1;
                    int geoDef = nodeHasTexture ? 1 : 0;

                    json.append("      \"mesh\": {")
                            .append("\"material\": {\"definition\": ").append(matDef)
                            .append(", \"resource\": ").append(matRes).append("}, ")
                            .append("\"geometry\": {\"definition\": ").append(geoDef)
                            .append(", \"resource\": ").append(node.getIndex())
                            .append(", \"vertexCount\": ").append(vtxCount)
                            .append(", \"featureCount\": ").append(ftCount)
                            .append("}, \"attribute\": {\"resource\": ").append(node.getIndex()).append("}},\n");
                } else {
                    json.append("      \"mesh\": null,\n");
                }
                json.append("      \"featureCount\": ").append(node.getFeatureCount()).append("\n");
                json.append("    }");
            }

            json.append("\n  ]\n}\n");

            Path nodePageDir = layerDir.resolve("nodepages").resolve(String.valueOf(page));
            Files.createDirectories(nodePageDir);
            Files.write(nodePageDir.resolve("index.json"),
                    json.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Write per-node shared resource (legacy material/texture definitions).
     * CesiumJS reads this for texture metadata.
     */
    void writeSharedResource(Path layerDir, I3SNode node, boolean isAtlas) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\n");

        // Material definition (legacy format for CesiumJS compatibility)
        json.append("  \"materialDefinitions\": {\n");
        json.append("    \"Mat0\": {\n");
        json.append("      \"type\": \"standard\",\n");
        json.append("      \"name\": \"standard\",\n");
        json.append("      \"params\": {\n");
        json.append("        \"vertexColors\": false,\n");
        json.append("        \"reflectivity\": 0,\n");
        json.append("        \"ambient\": [0, 0, 0],\n");
        json.append("        \"diffuse\": [1, 1, 1],\n");
        json.append("        \"specular\": [0.09803921568, 0.09803921568, 0.09803921568],\n");
        json.append("        \"shininess\": 1,\n");
        json.append("        \"renderMode\": \"solid\",\n");
        json.append("        \"cullFace\": \"back\"\n");
        json.append("      }\n");
        json.append("    }\n");
        json.append("  },\n");

        // Texture definition
        json.append("  \"textureDefinitions\": {\n");
        json.append("    \"0\": {\n");
        json.append("      \"encoding\": [\"image/jpeg\"],\n");
        json.append("      \"wrap\": [\"none\", \"none\"],\n");
        json.append("      \"atlas\": ").append(isAtlas).append(",\n");
        json.append("      \"uvSet\": \"uv0\",\n");
        json.append("      \"channels\": \"rgb\",\n");
        json.append("      \"images\": [{\n");
        json.append("        \"id\": \"0\",\n");
        json.append("        \"size\": 512,\n");
        json.append("        \"pixelInWorldUnits\": 0,\n");
        json.append("        \"href\": [\"../textures/0\"]\n");
        json.append("      }]\n");
        json.append("    }\n");
        json.append("  }\n");

        json.append("}\n");

        Path sharedDir = layerDir.resolve("nodes").resolve(String.valueOf(node.getIndex()))
                .resolve("shared");
        Files.createDirectories(sharedDir);
        Files.write(sharedDir.resolve("index.json"),
                json.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Write per-node feature metadata to features/0/index.json.
     */
    void writeNodeFeatures(Path layerDir, I3SNode node,
                           List<FeatureData> features) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("[\n");
        if (features != null) {
            for (int i = 0; i < features.size(); i++) {
                FeatureData fd = features.get(i);
                if (i > 0) json.append(",\n");
                json.append("  {\"id\": ").append(fd.id());
                json.append(", \"objectId\": \"").append(escapeJson(fd.objectId())).append("\"");
                json.append(", \"featureType\": \"").append(escapeJson(fd.featureType())).append("\"}");
            }
        }
        json.append("\n]\n");

        Files.write(layerDir.resolve("nodes").resolve(String.valueOf(node.getIndex()))
                .resolve("features").resolve("0").resolve("index.json"),
                json.toString().getBytes(StandardCharsets.UTF_8));
    }

    static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
