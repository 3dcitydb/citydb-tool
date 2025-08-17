/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citydb.operation.exporter.util;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.citydb.model.appearance.SurfaceData;
import org.citydb.model.appearance.TextureCoordinate;

import java.util.*;
import java.util.stream.IntStream;

public class SurfaceDataMapper {
    private final Map<SurfaceData<?>, List<String>> materialMappings = new IdentityHashMap<>();
    private final Map<SurfaceData<?>, Map<String, List<List<TextureCoordinate>>>> textureMappings = new IdentityHashMap<>();
    private final Map<SurfaceData<?>, Map<String, List<Double>>> worldToTextureMappings = new IdentityHashMap<>();
    private final Map<SurfaceData<?>, List<String>> georeferencedTextureMappings = new IdentityHashMap<>();

    public SurfaceDataMapper buildMaterialMapping(JSONObject mapping, long geometryDataId, SurfaceData<?> surfaceData) {
        if (mapping != null) {
            for (Map.Entry<String, Object> entry : mapping.entrySet()) {
                if (entry.getValue() == Boolean.TRUE) {
                    materialMappings.computeIfAbsent(surfaceData, v -> new ArrayList<>())
                            .add(getKey(geometryDataId, entry.getKey()));
                }
            }
        }

        return this;
    }

    public SurfaceDataMapper buildTextureMapping(JSONObject mapping, long geometryDataId, SurfaceData<?> surfaceData) {
        if (mapping != null) {
            for (Map.Entry<String, Object> entry : mapping.entrySet()) {
                if (entry.getValue() instanceof JSONArray rings) {
                    List<List<TextureCoordinate>> textureCoordinates = new ArrayList<>();
                    for (Object ring : rings) {
                        if (ring instanceof JSONArray ringArray) {
                            List<TextureCoordinate> ringCoordinates = new ArrayList<>();
                            for (Object coordinates : ringArray) {
                                if (coordinates instanceof JSONArray coordinatesArray && coordinatesArray.size() > 1) {
                                    Float s = coordinatesArray.getFloat(0);
                                    Float t = coordinatesArray.getFloat(1);
                                    if (s != null && t != null) {
                                        ringCoordinates.add(TextureCoordinate.of(s, t));
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

    public SurfaceDataMapper buildWorldToTextureMapping(JSONObject mapping, long geometryDataId, SurfaceData<?> surfaceData) {
        if (mapping != null) {
            for (Map.Entry<String, Object> entry : mapping.entrySet()) {
                if (entry.getValue() instanceof JSONArray array) {
                    List<Double> worldToTexture = IntStream.range(0, array.size())
                            .mapToObj(array::getDouble)
                            .toList();
                    worldToTextureMappings.computeIfAbsent(surfaceData, v -> new HashMap<>())
                            .put(getKey(geometryDataId, entry.getKey()), worldToTexture);
                }
            }
        }

        return this;
    }

    public SurfaceDataMapper buildGeoreferencedTextureMapping(JSONObject mapping, long geometryDataId, SurfaceData<?> surfaceData) {
        if (mapping != null) {
            for (Map.Entry<String, Object> entry : mapping.entrySet()) {
                if (entry.getValue() == Boolean.TRUE) {
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
