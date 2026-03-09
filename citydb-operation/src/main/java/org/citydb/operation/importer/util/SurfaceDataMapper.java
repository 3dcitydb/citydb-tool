/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.importer.util;

import com.alibaba.fastjson2.JSONArray;
import org.citydb.model.appearance.*;
import org.citydb.model.common.Matrix3x4;
import org.citydb.model.geometry.GeometryDescriptor;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.geometry.Polygon;
import org.citydb.model.geometry.Surface;
import org.citydb.operation.importer.ImportException;

import java.util.*;

public class SurfaceDataMapper {

    private SurfaceDataMapper() {
    }

    public static SurfaceDataMapper newInstance() {
        return new SurfaceDataMapper();
    }

    public Map<Long, SurfaceDataMapping> createMapping(SurfaceData<?> surfaceData) throws ImportException {
        Map<Long, List<Surface<?>>> surfaces = new HashMap<>();
        for (Surface<?> surface : surfaceData.getTargets()) {
            GeometryDescriptor descriptor = surface.getRootGeometry().getDescriptor().orElse(null);
            if (descriptor == null) {
                throw new ImportException("Failed to link surface data to target geometry.");
            }

            surfaces.computeIfAbsent(descriptor.getId(), v -> new ArrayList<>()).add(surface);
        }

        if (surfaceData instanceof ParameterizedTexture texture) {
            return getParameterizedTextureMapping(texture, surfaces);
        } else if (surfaceData instanceof X3DMaterial) {
            return getMaterialMapping(surfaces);
        } else if (surfaceData instanceof GeoreferencedTexture) {
            return getGeoreferencedTextureMapping(surfaces);
        } else {
            return Collections.emptyMap();
        }
    }

    private Map<Long, SurfaceDataMapping> getParameterizedTextureMapping(ParameterizedTexture texture, Map<Long, List<Surface<?>>> surfaces) {
        Map<Long, SurfaceDataMapping> mappings = new HashMap<>();
        for (Map.Entry<Long, List<Surface<?>>> entry : surfaces.entrySet()) {
            SurfaceDataMapping mapping = new SurfaceDataMapping();

            for (Surface<?> surface : entry.getValue()) {
                String objectId = surface.getObjectId().orElse(null);
                if (objectId != null) {
                    Matrix3x4 worldToTexture = texture.getWorldToTextureMapping(surface);
                    if (worldToTexture != null) {
                        mapping.getOrCreateWorldToTextureMapping()
                                .put(objectId, new JSONArray(worldToTexture.toRowMajor()));
                    }

                    if (surface instanceof Polygon polygon) {
                        JSONArray rings = new JSONArray(polygon.getRings().size());
                        for (LinearRing ring : polygon.getRings()) {
                            List<TextureCoordinate> coordinates = texture.getTextureCoordinates(ring);
                            if (coordinates != null) {
                                JSONArray ringCoordinates = new JSONArray(coordinates.size());
                                coordinates.stream()
                                        .map(coordinate -> JSONArray.of(coordinate.getS(), coordinate.getT()))
                                        .forEach(ringCoordinates::add);
                                rings.add(ringCoordinates);
                            }
                        }

                        if (!rings.isEmpty()) {
                            mapping.getOrCreateTextureMapping().put(objectId, rings);
                        }
                    }
                }
            }

            mappings.put(entry.getKey(), mapping);
        }

        return mappings;
    }

    private Map<Long, SurfaceDataMapping> getMaterialMapping(Map<Long, List<Surface<?>>> surfaces) {
        Map<Long, SurfaceDataMapping> mappings = new HashMap<>();
        for (Map.Entry<Long, List<Surface<?>>> entry : surfaces.entrySet()) {
            SurfaceDataMapping mapping = new SurfaceDataMapping();
            for (Surface<?> surface : entry.getValue()) {
                surface.getObjectId().ifPresent(objectId ->
                        mapping.getOrCreateMaterialMapping().put(objectId, true));
            }

            mappings.put(entry.getKey(), mapping);
        }

        return mappings;
    }

    private Map<Long, SurfaceDataMapping> getGeoreferencedTextureMapping(Map<Long, List<Surface<?>>> surfaces) {
        Map<Long, SurfaceDataMapping> mappings = new HashMap<>();
        for (Map.Entry<Long, List<Surface<?>>> entry : surfaces.entrySet()) {
            SurfaceDataMapping mapping = new SurfaceDataMapping();
            for (Surface<?> surface : entry.getValue()) {
                surface.getObjectId().ifPresent(objectId ->
                        mapping.getOrCreateGeoreferencedTextureMapping().put(objectId, true));
            }

            mappings.put(entry.getKey(), mapping);
        }

        return mappings;
    }
}
