/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.util;

import org.citydb.model.appearance.*;
import org.citydb.model.common.DatabaseDescriptor;
import org.citydb.model.common.Visitable;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.geometry.Polygon;
import org.citydb.model.geometry.Surface;
import org.citydb.model.property.AppearanceProperty;
import org.citydb.model.walker.ModelWalker;
import org.citydb.operation.exporter.ExportHelper;

import java.util.*;

public class AppearanceHelper {
    private final ExportHelper helper;

    AppearanceHelper(ExportHelper helper) {
        this.helper = helper;
    }

    void assignSurfaceData(Visitable visitable, SurfaceDataMapper surfaceDataMapper) {
        Map<String, Surface<?>> surfaces = new HashMap<>();
        visitable.accept(new ModelWalker() {
            @Override
            public void visit(Surface<?> surface) {
                long geometryDataId = surface.getRootGeometry().getDescriptor()
                        .map(DatabaseDescriptor::getId)
                        .orElse(0L);
                surface.getObjectId().ifPresent(objectId -> surfaces.put(geometryDataId + "#" + objectId, surface));
                super.visit(surface);
            }
        });

        visitable.accept(new ModelWalker() {
            @Override
            public void visit(X3DMaterial material) {
                surfaceDataMapper.getMaterialMappings(material).stream()
                        .map(surfaces::get)
                        .filter(Objects::nonNull)
                        .forEach(material::addTarget);
            }

            @Override
            public void visit(ParameterizedTexture texture) {
                for (Map.Entry<String, List<Double>> entry : surfaceDataMapper
                        .getWorldToTextureMappings(texture).entrySet()) {
                    Surface<?> surface = surfaces.get(entry.getKey());
                    if (surface != null) {
                        texture.addWorldToTextureMapping(surface, entry.getValue());
                    }
                }

                for (Map.Entry<String, List<List<TextureCoordinate>>> entry : surfaceDataMapper
                        .getTextureMappings(texture).entrySet()) {
                    Surface<?> surface = surfaces.get(entry.getKey());
                    if (surface instanceof Polygon polygon) {
                        List<LinearRing> rings = polygon.getRings();
                        if (rings.size() == entry.getValue().size()) {
                            for (int i = 0; i < rings.size(); i++) {
                                List<TextureCoordinate> textureCoordinates = entry.getValue().get(i);
                                if (textureCoordinates != null) {
                                    LinearRing ring = rings.get(i).setObjectId(helper.createId());
                                    texture.addTextureCoordinates(ring, textureCoordinates);
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void visit(GeoreferencedTexture texture) {
                surfaceDataMapper.getGeoreferencedTextureMappings(texture).stream()
                        .map(surfaces::get)
                        .filter(Objects::nonNull)
                        .forEach(texture::addTarget);
            }
        });
    }

    Set<String> removeEmptySurfaceData(Feature feature) {
        Set<String> surfaceDataIds = new HashSet<>();
        feature.accept(new ModelWalker() {
            @Override
            public void visit(Appearance appearance) {
                Iterator<SurfaceDataProperty> iterator = appearance.getSurfaceData().iterator();
                while (iterator.hasNext()) {
                    SurfaceData<?> surfaceData = iterator.next().getObject().orElse(null);
                    if ((surfaceData instanceof ParameterizedTexture texture
                            && !texture.hasTextureCoordinates() && !texture.hasWorldToTextureMappings())
                            || (surfaceData instanceof X3DMaterial material && !material.hasTargets())
                            || (surfaceData instanceof GeoreferencedTexture geoTexture && !geoTexture.hasTargets())) {
                        surfaceData.getObjectId().ifPresent(surfaceDataIds::add);
                        iterator.remove();
                    }
                }
            }
        });

        return surfaceDataIds;
    }

    void removeEmptyAppearances(Feature feature) {
        feature.accept(new ModelWalker() {
            @Override
            public void visit(AppearanceProperty property) {
                if (!property.getObject().hasSurfaceData()) {
                    property.removeFromParent();
                }
            }
        });
    }
}
