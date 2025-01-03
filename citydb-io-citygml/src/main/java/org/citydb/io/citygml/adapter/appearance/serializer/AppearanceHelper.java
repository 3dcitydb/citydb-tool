/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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

package org.citydb.io.citygml.adapter.appearance.serializer;

import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.serializer.ModelSerializer;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.model.appearance.SurfaceData;
import org.citydb.model.appearance.SurfaceDataProperty;
import org.citydb.model.appearance.TextureCoordinate;
import org.citydb.model.common.Child;
import org.citydb.model.common.Matrix3x4;
import org.citydb.model.common.Reference;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.geometry.Surface;
import org.citydb.model.geometry.TriangulatedSurface;
import org.citygml4j.core.model.appearance.*;
import org.citygml4j.core.model.core.TransformationMatrix3x4;
import org.citygml4j.core.visitor.ObjectWalker;
import org.citygml4j.core.visitor.VisitableObject;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.aggregates.MultiSurface;
import org.xmlobjects.gml.model.geometry.primitives.AbstractSurface;

import java.util.*;

public class AppearanceHelper {
    private final ModelSerializerHelper helper;
    private final AppearanceSerializer serializer;
    private final Map<SurfaceData<?>, AbstractSurfaceData> surfaceData = new IdentityHashMap<>();
    private final Set<String> surfaceDataIdCache = new HashSet<>();

    public AppearanceHelper(ModelSerializerHelper helper) {
        this.helper = helper;
        serializer = new AppearanceSerializer(this);
    }

    public Appearance getAppearance(org.citydb.model.appearance.Appearance source) throws ModelSerializeException {
        return source != null ? buildObject(source, serializer) : null;
    }

    @SuppressWarnings("rawtypes")
    AbstractSurfaceDataProperty getSurfaceDataProperty(SurfaceDataProperty source) throws ModelSerializeException {
        AbstractSurfaceDataProperty property = new AbstractSurfaceDataProperty();
        SurfaceData<?> surfaceData = source.getObject().orElse(null);
        if (surfaceData != null) {
            if (lookupAndPut(surfaceData)) {
                property.setHref("#" + surfaceData.getOrCreateObjectId());
            } else {
                ModelSerializer<SurfaceData, AbstractSurfaceData> serializer = helper.getContext()
                        .getSerializer(surfaceData.getName(), SurfaceData.class, AbstractSurfaceData.class);
                if (serializer != null) {
                    property.setInlineObjectIfValid(buildObject(surfaceData, serializer));
                    if (property.getObject() != null) {
                        this.surfaceData.put(surfaceData, property.getObject());
                    }
                }
            }
        } else {
            source.getReference()
                    .map(Reference::getTarget)
                    .ifPresent(reference -> property.setHref("#" + reference));
        }

        return property;
    }

    public void processTargets(VisitableObject object) {
        if (!surfaceData.isEmpty()) {
            GeometryCollector collector = new GeometryCollector();
            Set<String> geometries = collector.collect(object);
            surfaceData.forEach((k, v) -> {
                if (k instanceof org.citydb.model.appearance.ParameterizedTexture source
                        && v instanceof ParameterizedTexture target) {
                    process(source, target, geometries);
                } else if (k instanceof org.citydb.model.appearance.X3DMaterial source
                        && v instanceof X3DMaterial target) {
                    process(source, target, geometries);
                } else if (k instanceof org.citydb.model.appearance.GeoreferencedTexture source
                        && v instanceof GeoreferencedTexture target) {
                    process(source, target, geometries);
                }
            });
        }
    }

    private void process(org.citydb.model.appearance.ParameterizedTexture source, ParameterizedTexture target, Set<String> geometries) {
        if (source.hasTextureCoordinates()) {
            addTextureTargets(source.getTextureCoordinates(), target, geometries);
        }

        if (source.hasWorldToTextureMappings()) {
            addWorldToTextureTargets(source.getWorldToTextureMappings(), target, geometries);
        }
    }

    private void process(org.citydb.model.appearance.X3DMaterial source, X3DMaterial target, Set<String> geometries) {
        if (source.hasTargets()) {
            addSurfaceTargets(source.getTargets(), target.getTargets(), geometries);
        }
    }

    private void process(org.citydb.model.appearance.GeoreferencedTexture source, GeoreferencedTexture target, Set<String> geometries) {
        if (source.hasTargets()) {
            addSurfaceTargets(source.getTargets(), target.getTargets(), geometries);
        }
    }

    private void addTextureTargets(Map<LinearRing, List<TextureCoordinate>> mapping, ParameterizedTexture target, Set<String> geometries) {
        Map<String, TexCoordList> textureCoordinates = new HashMap<>();
        mapping.forEach((k, v) -> {
            String ringId = k.getOrCreateObjectId();
            if (geometries.contains(ringId)) {
                Surface<?> parent = k.getParent(Surface.class);
                Geometry<?> root = parent.getRootGeometry();
                String surfaceId = root instanceof TriangulatedSurface ?
                        root.getOrCreateObjectId() :
                        parent.getOrCreateObjectId();
                textureCoordinates.computeIfAbsent(surfaceId, t -> new TexCoordList())
                        .getTextureCoordinates()
                        .add(new TextureCoordinates(getTextureCoordinates(v), "#" + ringId));
            }
        });

        textureCoordinates.forEach((k, v) -> target.getTextureParameterizations()
                .add(new TextureAssociationProperty(new TextureAssociation(
                        "#" + k, new AbstractTextureParameterizationProperty(v)))));
    }

    public void addWorldToTextureTargets(Map<Surface<?>, Matrix3x4> mapping, ParameterizedTexture target, Set<String> geometries) {
        Set<String> targets = new HashSet<>();
        mapping.forEach((k, v) -> {
            String surfaceId = k.getOrCreateObjectId();
            if (geometries.contains(surfaceId) && targets.add(surfaceId)) {
                TexCoordGen texCoordGen = new TexCoordGen();
                texCoordGen.setWorldToTexture(TransformationMatrix3x4.ofRowMajorList(v.toRowMajor()));
                target.getTextureParameterizations().add(new TextureAssociationProperty(new TextureAssociation(
                        "#" + surfaceId, new AbstractTextureParameterizationProperty(texCoordGen))));
            }
        });
    }

    private void addSurfaceTargets(List<Surface<?>> surfaces, List<GeometryReference> references, Set<String> geometries) {
        Set<String> targets = new HashSet<>();
        surfaces.stream()
                .map(Surface::getOrCreateObjectId)
                .filter(geometries::contains)
                .filter(targets::add)
                .forEach(surface -> references.add(new GeometryReference("#" + surface)));
    }

    private List<Double> getTextureCoordinates(List<TextureCoordinate> textureCoordinates) {
        if (textureCoordinates != null) {
            List<Double> values = new ArrayList<>();
            textureCoordinates.forEach(textureCoordinate -> {
                values.add(textureCoordinate.getS());
                values.add(textureCoordinate.getT());
            });

            return values;
        }

        return null;
    }

    private boolean lookupAndPut(SurfaceData<?> surfaceData) {
        String objectId = surfaceData.getObjectId().orElse(null);
        return objectId != null && !surfaceDataIdCache.add(objectId);
    }

    private <T extends Child, R> R buildObject(T source, ModelSerializer<T, R> serializer) throws ModelSerializeException {
        R target = serializer.createObject(source);
        if (target != null) {
            serializer.serialize(source, target, helper);
            serializer.postSerialize(source, target, helper);
            return target;
        } else {
            throw new ModelSerializeException("The serializer " + serializer.getClass().getName() +
                    " returned a null object.");
        }
    }

    public void reset() {
        surfaceData.clear();
        surfaceDataIdCache.clear();
    }

    private static class GeometryCollector extends ObjectWalker {
        private final Set<String> geometries = new HashSet<>();

        Set<String> collect(VisitableObject object) {
            object.accept(this);
            return geometries;
        }

        @Override
        public void visit(AbstractSurface surface) {
            collectId(surface);
            super.visit(surface);
        }

        @Override
        public void visit(MultiSurface multiSurface) {
            collectId(multiSurface);
            super.visit(multiSurface);
        }

        @Override
        public void visit(org.xmlobjects.gml.model.geometry.primitives.LinearRing linearRing) {
            collectId(linearRing);
            super.visit(linearRing);
        }

        private void collectId(AbstractGeometry geometry) {
            if (geometry.getId() != null) {
                geometries.add(geometry.getId());
            }
        }
    }
}
