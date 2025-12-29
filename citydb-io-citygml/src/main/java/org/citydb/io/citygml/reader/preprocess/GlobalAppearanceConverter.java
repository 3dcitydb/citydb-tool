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

package org.citydb.io.citygml.reader.preprocess;

import org.citydb.io.citygml.reader.util.FeatureHelper;
import org.citygml4j.core.model.appearance.*;
import org.citygml4j.core.model.core.AbstractAppearanceProperty;
import org.citygml4j.core.model.core.AbstractCityObject;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.core.model.deprecated.appearance.DeprecatedPropertiesOfParameterizedTexture;
import org.citygml4j.core.model.deprecated.appearance.TextureAssociationReference;
import org.citygml4j.core.util.reference.DefaultReferenceResolver;
import org.citygml4j.core.visitor.ObjectWalker;
import org.citygml4j.core.visitor.VisitableObject;
import org.xmlobjects.gml.model.base.AbstractGML;
import org.xmlobjects.gml.model.base.AbstractInlineOrByReferenceProperty;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.GeometryProperty;
import org.xmlobjects.model.Child;
import org.xmlobjects.util.copy.CopyBuilder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GlobalAppearanceConverter {
    private final CopyBuilder copyBuilder;
    private final Map<String, List<AbstractSurfaceData>> targets = new ConcurrentHashMap<>();
    private final String ID = "id";

    private Mode mode = Mode.TOPLEVEL;

    public enum Mode {
        TOPLEVEL,
        CHILD;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    GlobalAppearanceConverter(CopyBuilder copyBuilder) {
        this.copyBuilder = copyBuilder;
    }

    GlobalAppearanceConverter setMode(Mode mode) {
        this.mode = mode;
        return this;
    }

    boolean hasResolvableTargets() {
        return !targets.isEmpty();
    }

    void preprocess(List<Appearance> appearances) {
        if (!appearances.isEmpty()) {
            ObjectWalker preprocessor = new ObjectWalker() {
                private int id;

                @Override
                public void visit(AbstractFeature feature) {
                    feature.getLocalProperties().set(ID, id++);
                }

                @Override
                public void visit(AbstractInlineOrByReferenceProperty<?> property) {
                    if (property.isSetReferencedObject()) {
                        Child child = property.getObject();
                        property.setInlineObjectIfValid(copyBuilder.shallowCopy(child));
                        property.setHref(null);
                    }

                    super.visit(property);
                }

                @Override
                public void visit(ParameterizedTexture texture) {
                    if (texture.hasDeprecatedProperties()) {
                        DeprecatedPropertiesOfParameterizedTexture properties = texture.getDeprecatedProperties();
                        Iterator<TextureAssociationReference> iterator = properties.getTargets().iterator();
                        while (iterator.hasNext()) {
                            TextureAssociationReference reference = iterator.next();
                            if (reference.isSetReferencedObject()) {
                                TextureAssociation copy = copyBuilder.shallowCopy(reference.getReferencedObject());
                                texture.getTextureParameterizations().add(new TextureAssociationProperty(copy));
                                iterator.remove();
                            } else if (reference.getURI() != null) {
                                targets.computeIfAbsent(
                                        FeatureHelper.getIdFromReference(reference.getURI()),
                                        v -> new ArrayList<>()).add(texture);
                            }
                        }
                    }

                    for (TextureAssociationProperty property : texture.getTextureParameterizations()) {
                        GeometryReference reference = getGeometryReference(property);
                        if (reference != null && reference.getHref() != null) {
                            targets.computeIfAbsent(
                                    FeatureHelper.getIdFromReference(reference.getHref()),
                                    v -> new ArrayList<>()).add(texture);
                        }
                    }

                    super.visit(texture);
                }

                @Override
                public void visit(GeoreferencedTexture texture) {
                    for (GeometryReference reference : texture.getTargets()) {
                        if (reference.getHref() != null) {
                            targets.computeIfAbsent(
                                    FeatureHelper.getIdFromReference(reference.getHref()),
                                    v -> new ArrayList<>()).add(texture);
                        }
                    }

                    super.visit(texture);
                }

                @Override
                public void visit(X3DMaterial material) {
                    for (GeometryReference reference : material.getTargets()) {
                        if (reference.getHref() != null) {
                            targets.computeIfAbsent(
                                    FeatureHelper.getIdFromReference(reference.getHref()),
                                    v -> new ArrayList<>()).add(material);
                        }
                    }

                    super.visit(material);
                }
            };

            DefaultReferenceResolver.newInstance().resolveReferences(appearances);
            appearances.forEach(preprocessor::visit);
        }
    }

    void convertGlobalAppearance(VisitableObject object) {
        if (!targets.isEmpty()) {
            AppearanceProcessor processor = new AppearanceProcessor(object);
            object.accept(processor);
        }
    }

    private GeometryReference getGeometryReference(TextureAssociationProperty property) {
        return property.getObject() != null && property.getObject().getTarget() != null ?
                property.getObject().getTarget() :
                null;
    }

    private class AppearanceProcessor extends ObjectWalker {
        private final AbstractCityObject topLevelFeature;

        AppearanceProcessor(VisitableObject object) {
            topLevelFeature = object instanceof AbstractCityObject cityObject ?
                    cityObject :
                    object.getParent(AbstractCityObject.class);
        }

        @Override
        public void visit(AbstractGeometry geometry) {
            if (geometry.getId() != null) {
                List<AbstractSurfaceData> sources = targets.remove(geometry.getId());
                if (sources != null) {
                    for (AbstractSurfaceData source : sources) {
                        AbstractGML target = getTargetObject(geometry);
                        if (target != null) {
                            convertAppearance(target, source, geometry);
                        }
                    }
                }
            }
        }

        @Override
        public void visit(GeometryProperty<?> property) {
            if (property.isSetInlineObject()) {
                super.visit(property);
            }
        }

        private AbstractGML getTargetObject(AbstractGeometry geometry) {
            ImplicitGeometry implicitGeometry = geometry.getParent(ImplicitGeometry.class);
            if (implicitGeometry != null) {
                return implicitGeometry;
            } else if (mode == Mode.TOPLEVEL && topLevelFeature != null) {
                return topLevelFeature;
            } else {
                return geometry.getParent(AbstractCityObject.class);
            }
        }

        private void convertAppearance(AbstractGML target, AbstractSurfaceData source, AbstractGeometry geometry) {
            Appearance appearance = source.getParent(Appearance.class);
            AbstractSurfaceData surfaceData = getOrCreateSurfaceData(target, appearance, source);
            if (surfaceData instanceof ParameterizedTexture targetTexture) {
                ParameterizedTexture texture = (ParameterizedTexture) source;
                for (TextureAssociationProperty property : texture.getTextureParameterizations()) {
                    GeometryReference reference = getGeometryReference(property);
                    if (reference != null
                            && reference.getHref() != null
                            && FeatureHelper.getIdFromReference(reference.getHref()).equals(geometry.getId())) {
                        targetTexture.getTextureParameterizations().add(property);
                    }
                }
            } else if (surfaceData instanceof X3DMaterial material) {
                material.getTargets().add(new GeometryReference("#" + geometry.getId()));
            } else if (surfaceData instanceof GeoreferencedTexture texture) {
                texture.getTargets().add(new GeometryReference("#" + geometry.getId()));
            }
        }

        private Appearance getOrCreateAppearance(AbstractGML target, Appearance globalAppearance) {
            List<AbstractAppearanceProperty> appearances = target instanceof AbstractCityObject ?
                    ((AbstractCityObject) target).getAppearances() :
                    ((ImplicitGeometry) target).getAppearances();

            for (AbstractAppearanceProperty property : appearances) {
                if (property.getObject() instanceof Appearance appearance) {
                    if (globalAppearance.getLocalProperties().getAndCompare(ID, appearance.getLocalProperties().get(ID))) {
                        return appearance;
                    }
                }
            }

            Appearance appearance = copyBuilder.shallowCopy(globalAppearance);
            appearance.setId(target instanceof ImplicitGeometry ? FeatureHelper.createId() : null);
            appearance.setSurfaceData(null);
            appearance.setLocalProperties(null);
            appearance.getLocalProperties().set(ID, globalAppearance.getLocalProperties().get(ID));
            appearances.add(new AbstractAppearanceProperty(appearance));

            return appearance;
        }

        private AbstractSurfaceData getOrCreateSurfaceData(AbstractGML target, Appearance globalAppearance, AbstractSurfaceData globalSurfaceData) {
            Appearance appearance = getOrCreateAppearance(target, globalAppearance);
            for (AbstractSurfaceDataProperty property : appearance.getSurfaceData()) {
                if (property.getObject() != null) {
                    AbstractSurfaceData surfaceData = property.getObject();
                    if (globalSurfaceData.getLocalProperties().getAndCompare(ID, surfaceData.getLocalProperties().get(ID))) {
                        return surfaceData;
                    }
                }
            }

            AbstractSurfaceData surfaceData = copyBuilder.shallowCopy(globalSurfaceData);
            surfaceData.setId(null);
            surfaceData.setLocalProperties(null);
            surfaceData.getLocalProperties().set(ID, globalSurfaceData.getLocalProperties().get(ID));
            appearance.getSurfaceData().add(new AbstractSurfaceDataProperty(surfaceData));

            if (surfaceData instanceof ParameterizedTexture texture) {
                texture.setTextureParameterizations(null);
            } else if (surfaceData instanceof X3DMaterial material) {
                material.setTargets(null);
            } else if (surfaceData instanceof GeoreferencedTexture texture) {
                texture.setTargets(null);
            }

            return surfaceData;
        }
    }
}
