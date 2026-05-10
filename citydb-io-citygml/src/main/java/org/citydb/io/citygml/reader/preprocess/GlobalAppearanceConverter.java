/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.reader.preprocess;

import org.citydb.io.citygml.reader.util.FeatureHelper;
import org.citygml4j.core.model.appearance.*;
import org.citygml4j.core.model.core.AbstractAppearanceProperty;
import org.citygml4j.core.model.core.AbstractCityObject;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.core.model.deprecated.appearance.DeprecatedPropertiesOfParameterizedTexture;
import org.citygml4j.core.model.deprecated.appearance.TextureAssociationReference;
import org.citygml4j.core.util.reference.DefaultReferenceResolver;
import org.citygml4j.core.visitor.ObjectWalker;
import org.citygml4j.core.visitor.VisitableObject;
import org.xmlobjects.copy.Copier;
import org.xmlobjects.copy.CopySession;
import org.xmlobjects.gml.model.base.AbstractGML;
import org.xmlobjects.gml.model.base.AbstractInlineOrByReferenceProperty;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.GeometryProperty;
import org.xmlobjects.model.Child;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GlobalAppearanceConverter {
    private final Copier copier;
    private final Map<String, List<AbstractSurfaceData>> targets = new ConcurrentHashMap<>();

    private Mode mode = Mode.TOPLEVEL;

    public enum Mode {
        TOPLEVEL,
        CHILD;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    GlobalAppearanceConverter(Copier copier) {
        this.copier = copier;
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

                @Override
                public void visit(AbstractInlineOrByReferenceProperty<?> property) {
                    if (property.isSetReferencedObject()) {
                        Child child = property.getObject();
                        property.setInlineObjectIfValid(copier.shallowCopy(child));
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
                                TextureAssociation copy = copier.shallowCopy(reference.getReferencedObject());
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
            try (CopySession session = copier.createSession()) {
                object.accept(new AppearanceProcessor(object, session));
            }
        }
    }

    private GeometryReference getGeometryReference(TextureAssociationProperty property) {
        return property.getObject() != null && property.getObject().getTarget() != null
                ? property.getObject().getTarget()
                : null;
    }

    private class AppearanceProcessor extends ObjectWalker {
        private final AbstractCityObject topLevelFeature;
        private final CopySession session;

        AppearanceProcessor(VisitableObject object, CopySession session) {
            this.session = session;
            topLevelFeature = object instanceof AbstractCityObject cityObject
                    ? cityObject
                    : object.getParent(AbstractCityObject.class);
        }

        @Override
        public void visit(AbstractGeometry geometry) {
            if (geometry.getId() != null) {
                List<AbstractSurfaceData> sources = targets.get(geometry.getId());
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
            Appearance appearance = session.lookupClone(globalAppearance, Appearance.class);
            if (appearance != null) {
                return appearance;
            }

            List<AbstractAppearanceProperty> appearances = target instanceof AbstractCityObject cityObject
                    ? cityObject.getAppearances()
                    : ((ImplicitGeometry) target).getAppearances();

            appearance = copier.shallowCopy(globalAppearance, session);
            appearance.setId(target instanceof ImplicitGeometry ? FeatureHelper.createId() : null);
            appearance.setSurfaceData(null);
            appearance.setLocalProperties(null);
            appearances.add(new AbstractAppearanceProperty(appearance));

            return appearance;
        }

        private AbstractSurfaceData getOrCreateSurfaceData(AbstractGML target, Appearance globalAppearance, AbstractSurfaceData globalSurfaceData) {
            AbstractSurfaceData surfaceData = session.lookupClone(globalSurfaceData, AbstractSurfaceData.class);
            if (surfaceData != null) {
                return surfaceData;
            }

            surfaceData = copier.shallowCopy(globalSurfaceData, session);
            surfaceData.setId(null);
            surfaceData.setLocalProperties(null);

            if (surfaceData instanceof ParameterizedTexture texture) {
                texture.setTextureParameterizations(null);
            } else if (surfaceData instanceof X3DMaterial material) {
                material.setTargets(null);
            } else if (surfaceData instanceof GeoreferencedTexture texture) {
                texture.setTargets(null);
            }

            Appearance appearance = getOrCreateAppearance(target, globalAppearance);
            appearance.getSurfaceData().add(new AbstractSurfaceDataProperty(surfaceData));

            return surfaceData;
        }
    }
}
