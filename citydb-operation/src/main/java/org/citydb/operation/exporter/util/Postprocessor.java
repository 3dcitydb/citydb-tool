/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

import org.citydb.model.appearance.GeoreferencedTexture;
import org.citydb.model.appearance.ParameterizedTexture;
import org.citydb.model.appearance.TextureCoordinate;
import org.citydb.model.appearance.X3DMaterial;
import org.citydb.model.common.DatabaseDescriptor;
import org.citydb.model.common.Visitable;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.geometry.Polygon;
import org.citydb.model.geometry.Surface;
import org.citydb.model.property.Attribute;
import org.citydb.model.property.Property;
import org.citydb.model.util.IdCreator;
import org.citydb.model.walker.ModelWalker;

import java.util.*;

public class Postprocessor {
    private final SurfaceDataMapper surfaceDataMapper = new SurfaceDataMapper();
    private final Map<String, Surface<?>> surfaces = new HashMap<>();
    private final SurfaceCollector surfaceCollector = new SurfaceCollector();
    private final Processor processor = new Processor();
    private final Comparator<Property<?>> comparator = Comparator.comparingLong(
            property -> property.getDescriptor()
                    .map(DatabaseDescriptor::getId)
                    .orElse(0L));

    public SurfaceDataMapper getSurfaceDataMapper() {
        return surfaceDataMapper;
    }

    public void process(Visitable visitable) {
        try {
            visitable.accept(surfaceCollector);
            visitable.accept(processor);
        } finally {
            surfaceDataMapper.clear();
            surfaces.clear();
        }
    }

    private class SurfaceCollector extends ModelWalker {
        @Override
        public void visit(Surface<?> surface) {
            long geometryDataId = surface.getRootGeometry().getDescriptor()
                    .map(DatabaseDescriptor::getId)
                    .orElse(0L);
            surface.getObjectId().ifPresent(objectId -> surfaces.put(geometryDataId + "#" + objectId, surface));
            super.visit(surface);
        }
    }

    private class Processor extends ModelWalker {
        private final IdCreator idCreator = IdCreator.getInstance();

        @Override
        public void visit(Feature feature) {
            super.visit(feature);

            if (feature.hasAttributes()) {
                feature.getAttributes().sortPropertiesWithIdenticalNames(comparator);
            }

            if (feature.hasGeometries()) {
                feature.getGeometries().sortPropertiesWithIdenticalNames(comparator);
            }

            if (feature.hasImplicitGeometries()) {
                feature.getImplicitGeometries().sortPropertiesWithIdenticalNames(comparator);
            }

            if (feature.hasFeatures()) {
                feature.getFeatures().sortPropertiesWithIdenticalNames(comparator);
            }

            if (feature.hasAppearances()) {
                feature.getAppearances().sortPropertiesWithIdenticalNames(comparator);
            }
        }

        @Override
        public void visit(Attribute attribute) {
            super.visit(attribute);
            if (attribute.hasProperties()) {
                attribute.getProperties().sortPropertiesWithIdenticalNames(comparator);
            }
        }

        @Override
        public void visit(X3DMaterial material) {
            surfaceDataMapper.getMaterialMappings(material).stream()
                    .map(surfaces::get)
                    .filter(Objects::nonNull)
                    .forEach(material::addTarget);
        }

        @Override
        public void visit(ParameterizedTexture texture) {
            for (Map.Entry<String, List<Double>> entry : surfaceDataMapper.getWorldToTextureMappings(texture).entrySet()) {
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
                                LinearRing ring = rings.get(i).setObjectId(idCreator.createId());
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
    }
}
