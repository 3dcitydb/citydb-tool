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

package org.citydb.io.citygml.adapter.appearance.builder;

import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.reader.options.AppearanceOptions;
import org.citydb.io.citygml.reader.options.FormatOptions;
import org.citydb.model.appearance.*;
import org.citydb.model.appearance.GeoreferencedTexture;
import org.citydb.model.appearance.ParameterizedTexture;
import org.citydb.model.appearance.X3DMaterial;
import org.citydb.model.common.Child;
import org.citydb.model.geometry.LinearRing;
import org.citydb.model.geometry.Polygon;
import org.citydb.model.geometry.Surface;
import org.citygml4j.core.model.appearance.*;
import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.core.AbstractAppearance;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.core.visitor.ObjectWalker;
import org.citygml4j.core.visitor.VisitableObject;
import org.xmlobjects.gml.model.GMLObject;
import org.xmlobjects.gml.model.feature.FeatureProperty;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.GeometryProperty;
import org.xmlobjects.gml.model.geometry.primitives.AbstractSurfacePatch;
import org.xmlobjects.gml.visitor.GeometryWalker;

import java.util.*;
import java.util.stream.Collectors;

public class AppearanceHelper {
    private final ModelBuilderHelper helper;
    private final AppearanceBuilder builder;
    private final AppearanceProcessor processor = new AppearanceProcessor();
    private final Map<AbstractSurfaceData, SurfaceData<?>> surfaceData = new IdentityHashMap<>();
    private final Map<GMLObject, List<TargetContext<Surface<?>>>> surfaces = new IdentityHashMap<>();
    private final Map<GMLObject, List<TargetContext<LinearRing>>> rings = new IdentityHashMap<>();
    private boolean processAppearances = true;
    private Set<String> themes;

    private record TargetContext<T extends Child>(T target, GeometryProperty<?> parent) {
    }

    public AppearanceHelper(ModelBuilderHelper helper) {
        this.helper = helper;
        builder = new AppearanceBuilder(this);
    }

    public AppearanceHelper initialize(FormatOptions<?> formatOptions) {
        AppearanceOptions options = formatOptions.getAppearanceOptions().orElseGet(AppearanceOptions::new);
        processAppearances = options.isReadAppearances();
        themes = options.hasThemes() ? options.getThemes() : null;
        return this;
    }

    public org.citydb.model.appearance.Appearance getAppearance(AbstractAppearance source) throws ModelBuildException {
        if (processAppearances
                && source instanceof Appearance appearance
                && (themes == null || themes.contains(appearance.getTheme()))) {
            org.citydb.model.appearance.Appearance target = org.citydb.model.appearance.Appearance.newInstance();
            builder.build(appearance, target, helper);
            return target;
        } else {
            return null;
        }
    }

    public void addSurfaceData(SurfaceData<?> surfaceData, AbstractSurfaceData source) {
        if (processAppearances && surfaceData != null && source != null) {
            this.surfaceData.put(source, surfaceData);
        }
    }

    public void addTarget(Surface<?> surface, GMLObject source, GeometryProperty<?> parent) {
        if (processAppearances && surface != null && source != null) {
            surfaces.computeIfAbsent(source, v -> new ArrayList<>()).add(new TargetContext<>(surface, parent));
        }
    }

    public void addTarget(LinearRing ring, AbstractGeometry source, GeometryProperty<?> parent) {
        if (processAppearances && ring != null && source != null) {
            rings.computeIfAbsent(source, v -> new ArrayList<>()).add(new TargetContext<>(ring, parent));
        }
    }

    public void processTargets(AbstractFeature feature) {
        if (processAppearances) {
            AppearanceCollector collector = new AppearanceCollector();
            for (AppearanceCollector.Context context : collector.collect(feature)) {
                for (Appearance appearance : context.appearances) {
                    processor.process(appearance, context.properties);
                }
            }
        }
    }

    public void reset() {
        surfaceData.clear();
        surfaces.clear();
        rings.clear();
    }

    private static class AppearanceCollector extends ObjectWalker {
        private final Map<VisitableObject, Context> contexts = new IdentityHashMap<>();

        Collection<Context> collect(AbstractFeature feature) {
            feature.accept(this);
            return contexts.values();
        }

        @Override
        public void visit(FeatureProperty<?> property) {
            if (property.getObject() instanceof Appearance appearance) {
                VisitableObject parent = getParent(property);
                contexts.computeIfAbsent(parent, this::getContext)
                        .appearances.add(appearance);
            } else {
                super.visit(property);
            }
        }

        private Context getContext(VisitableObject parent) {
            Set<GeometryProperty<?>> properties = Collections.newSetFromMap(new IdentityHashMap<>());
            parent.accept(new ObjectWalker() {
                @Override
                public void visit(GeometryProperty<?> property) {
                    properties.add(property);
                    super.visit(property);
                }
            });

            return new Context(properties);
        }

        private VisitableObject getParent(FeatureProperty<?> property) {
            org.xmlobjects.model.Child parent = property.getParent();
            if (parent instanceof AbstractFeature feature) {
                return feature;
            } else if (parent instanceof ImplicitGeometry implicitGeometry) {
                return implicitGeometry;
            } else {
                return parent.getParent(AbstractFeature.class);
            }
        }

        private static class Context {
            final Set<GeometryProperty<?>> properties;
            final Set<Appearance> appearances;

            Context(Set<GeometryProperty<?>> properties) {
                this.properties = properties;
                appearances = Collections.newSetFromMap(new IdentityHashMap<>());
            }
        }
    }

    private class AppearanceProcessor extends ObjectWalker {
        private Set<GeometryProperty<?>> contexts;

        void process(Appearance appearance, Set<GeometryProperty<?>> contexts) {
            this.contexts = contexts;
            appearance.accept(this);
            this.contexts = null;
        }

        @Override
        public void visit(org.citygml4j.core.model.appearance.ParameterizedTexture texture) {
            ParameterizedTexture target = getSurfaceData(texture, ParameterizedTexture.class);
            if (target != null && texture.isSetTextureParameterizations()) {
                for (TextureAssociationProperty property : texture.getTextureParameterizations()) {
                    if (property != null
                            && property.getObject() != null
                            && property.getObject().getTarget() != null
                            && property.getObject().getTextureParameterization() != null) {
                        GeometryReference reference = property.getObject().getTarget();
                        AbstractTextureParameterization parameterization = property.getObject()
                                .getTextureParameterization().getObject();
                        if (parameterization instanceof TexCoordList texCoordList) {
                            addMapping(reference, texCoordList, target);
                        } else if (parameterization instanceof TexCoordGen texCoordGen) {
                            addMapping(reference, texCoordGen, target);
                        }
                    }
                }
            }
        }

        @Override
        public void visit(org.citygml4j.core.model.appearance.GeoreferencedTexture texture) {
            GeoreferencedTexture target = getSurfaceData(texture, GeoreferencedTexture.class);
            if (target != null && texture.isSetTargets()) {
                texture.getTargets().forEach(reference -> getTargets(reference).forEach(target::addTarget));
            }
        }

        @Override
        public void visit(org.citygml4j.core.model.appearance.X3DMaterial material) {
            X3DMaterial target = getSurfaceData(material, X3DMaterial.class);
            if (target != null && material.isSetTargets()) {
                material.getTargets().forEach(reference -> getTargets(reference).forEach(target::addTarget));
            }
        }

        private void addMapping(GeometryReference reference, TexCoordList texCoordList, ParameterizedTexture target) {
            if (texCoordList.isSetTextureCoordinates()) {
                for (TextureCoordinates textureCoordinates : texCoordList.getTextureCoordinates()) {
                    GMLObject source = textureCoordinates.getRing().isSetReferencedObject() ?
                            textureCoordinates.getRing().getReferencedObject() :
                            reference.getReferencedObject();

                    rings.getOrDefault(source, Collections.emptyList()).stream()
                            .filter(this::isValidContext)
                            .map(TargetContext::target)
                            .forEach(ring -> target.addTextureCoordinates(ring,
                                    createTextureCoordinates(textureCoordinates, ring)));
                }
            }
        }

        private void addMapping(GeometryReference reference, TexCoordGen texCoordGen, ParameterizedTexture target) {
            if (texCoordGen.getWorldToTexture() != null) {
                getTargets(reference).forEach(surface ->
                        target.addWorldToTextureMapping(surface, texCoordGen.getWorldToTexture().toRowMajor()));
            }
        }

        private List<TextureCoordinate> createTextureCoordinates(TextureCoordinates textureCoordinates, LinearRing target) {
            if (textureCoordinates.isSetValue()) {
                List<Double> coordinates = textureCoordinates.getValue();
                if (coordinates.size() % 2 != 0) {
                    coordinates.add(0.0);
                }

                Polygon polygon = target.getParent(Polygon.class);
                if (polygon != null && polygon.isReversed()) {
                    for (int i = coordinates.size() - 2, j = 0; j < coordinates.size() / 2; i -= 2, j += 2) {
                        double s = coordinates.get(j);
                        double t = coordinates.get(j + 1);
                        coordinates.set(j, coordinates.get(i));
                        coordinates.set(j + 1, coordinates.get(i + 1));
                        coordinates.set(i, s);
                        coordinates.set(i + 1, t);
                    }
                }

                return TextureCoordinate.of(coordinates);
            } else {
                return Collections.emptyList();
            }
        }

        private <T extends SurfaceData<?>> T getSurfaceData(AbstractSurfaceData source, Class<T> type) {
            SurfaceData<?> target = surfaceData.get(source);
            return type.isInstance(target) ? type.cast(target) : null;
        }

        private List<Surface<?>> getTargets(GeometryReference reference) {
            if (reference.isSetReferencedObject()) {
                List<TargetContext<Surface<?>>> contexts = surfaces.get(reference.getReferencedObject());
                if (contexts != null) {
                    return contexts.stream()
                            .filter(this::isValidContext)
                            .map(TargetContext::target)
                            .collect(Collectors.toList());
                } else {
                    List<Surface<?>> targets = new ArrayList<>();
                    reference.getReferencedObject().accept(new GeometryWalker() {
                        @Override
                        public void visit(AbstractGeometry geometry) {
                            process(geometry);
                        }

                        @Override
                        public void visit(AbstractSurfacePatch surfacePatch) {
                            process(surfacePatch);
                        }

                        private void process(GMLObject geometry) {
                            surfaces.getOrDefault(geometry, Collections.emptyList()).stream()
                                    .filter(context -> isValidContext(context))
                                    .map(TargetContext::target)
                                    .forEach(targets::add);
                        }
                    });

                    return targets;
                }
            } else {
                return Collections.emptyList();
            }
        }

        private boolean isValidContext(TargetContext<?> context) {
            return context.parent() == null || contexts.contains(context.parent());
        }
    }
}
