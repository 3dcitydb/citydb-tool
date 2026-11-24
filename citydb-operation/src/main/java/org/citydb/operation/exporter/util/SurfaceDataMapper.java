/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.util;

import org.citydb.model.appearance.SurfaceData;
import org.citydb.model.appearance.TextureCoordinate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.stream.IntStream;

public class SurfaceDataMapper {
    private final Map<SurfaceData<?>, List<String>> materialMappings = new IdentityHashMap<>();
    private final Map<SurfaceData<?>, Map<String, List<List<TextureCoordinate>>>> textureMappings = new IdentityHashMap<>();
    private final Map<SurfaceData<?>, Map<String, List<Double>>> worldToTextureMappings = new IdentityHashMap<>();
    private final Map<SurfaceData<?>, List<String>> georeferencedTextureMappings = new IdentityHashMap<>();

    public SurfaceDataMapper buildMaterialMapping(ObjectNode mapping, long geometryDataId, SurfaceData<?> surfaceData) {
        if (mapping != null) {
            for (Map.Entry<String, JsonNode> entry : mapping.properties()) {
                if (entry.getValue().isBoolean() && entry.getValue().booleanValue()) {
                    materialMappings.computeIfAbsent(surfaceData, v -> new ArrayList<>())
                            .add(getKey(geometryDataId, entry.getKey()));
                }
            }
        }

        return this;
    }

    public SurfaceDataMapper buildTextureMapping(ObjectNode mapping, long geometryDataId, SurfaceData<?> surfaceData) {
        if (mapping != null) {
            for (Map.Entry<String, JsonNode> entry : mapping.properties()) {
                if (entry.getValue() instanceof ArrayNode rings) {
                    List<List<TextureCoordinate>> textureCoordinates = new ArrayList<>();
                    for (JsonNode ring : rings) {
                        if (ring instanceof ArrayNode ringArray) {
                            List<TextureCoordinate> ringCoordinates = new ArrayList<>();
                            for (JsonNode coordinates : ringArray) {
                                if (coordinates instanceof ArrayNode coordinatesArray && coordinatesArray.size() > 1) {
                                    JsonNode s = coordinatesArray.get(0);
                                    JsonNode t = coordinatesArray.get(1);
                                    if (s.isNumber() && t.isNumber()) {
                                        ringCoordinates.add(TextureCoordinate.of(s.floatValue(), t.floatValue()));
                                    }
                                }
                            }

                            if (!ringCoordinates.isEmpty()) {
                                textureCoordinates.add(ringCoordinates);
                            }
                        }
                    }

                    if (!textureCoordinates.isEmpty()) {
                        textureMappings.computeIfAbsent(surfaceData, v -> new HashMap<>())
                                .put(getKey(geometryDataId, entry.getKey()), textureCoordinates);
                    }
                }
            }
        }

        return this;
    }

    public SurfaceDataMapper buildWorldToTextureMapping(ObjectNode mapping, long geometryDataId, SurfaceData<?> surfaceData) {
        if (mapping != null) {
            for (Map.Entry<String, JsonNode> entry : mapping.properties()) {
                if (entry.getValue() instanceof ArrayNode array) {
                    List<Double> worldToTexture = IntStream.range(0, array.size())
                            .mapToObj(i -> array.get(i).isNumber() ? array.get(i).doubleValue() : null)
                            .toList();
                    worldToTextureMappings.computeIfAbsent(surfaceData, v -> new HashMap<>())
                            .put(getKey(geometryDataId, entry.getKey()), worldToTexture);
                }
            }
        }

        return this;
    }

    public SurfaceDataMapper buildGeoreferencedTextureMapping(ObjectNode mapping, long geometryDataId, SurfaceData<?> surfaceData) {
        if (mapping != null) {
            for (Map.Entry<String, JsonNode> entry : mapping.properties()) {
                if (entry.getValue().isBoolean() && entry.getValue().booleanValue()) {
                    georeferencedTextureMappings.computeIfAbsent(surfaceData, v -> new ArrayList<>())
                            .add(getKey(geometryDataId, entry.getKey()));
                }
            }
        }

        return this;
    }

    List<String> getMaterialMappings(SurfaceData<?> surfaceData) {
        return materialMappings.getOrDefault(surfaceData, Collections.emptyList());
    }

    Map<String, List<List<TextureCoordinate>>> getTextureMappings(SurfaceData<?> surfaceData) {
        return textureMappings.getOrDefault(surfaceData, Collections.emptyMap());
    }

    Map<String, List<Double>> getWorldToTextureMappings(SurfaceData<?> surfaceData) {
        return worldToTextureMappings.getOrDefault(surfaceData, Collections.emptyMap());
    }

    List<String> getGeoreferencedTextureMappings(SurfaceData<?> surfaceData) {
        return georeferencedTextureMappings.getOrDefault(surfaceData, Collections.emptyList());
    }

    private String getKey(long geometryDataId, String objectId) {
        return geometryDataId + "#" + objectId;
    }

    public void clear() {
        materialMappings.clear();
        textureMappings.clear();
        worldToTextureMappings.clear();
        georeferencedTextureMappings.clear();
    }
}
