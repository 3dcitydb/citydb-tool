/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.reader.preprocess;

import org.citygml4j.core.model.common.GeometryInfo;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.core.visitor.ObjectWalker;
import org.xmlobjects.copy.CopySession;
import org.xmlobjects.gml.model.GMLObject;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.GeometryProperty;
import org.xmlobjects.model.Child;

import java.util.*;

public class CrossLodReferenceResolver {
    private Mode mode = Mode.RESOLVE;
    private boolean copyAppearance = true;

    public enum Mode {
        RESOLVE,
        REMOVE_LOD4_REFERENCES
    }

    CrossLodReferenceResolver() {
    }

    CrossLodReferenceResolver setMode(Mode mode) {
        this.mode = mode;
        return this;
    }

    CrossLodReferenceResolver copyAppearance(boolean copyAppearance) {
        this.copyAppearance = copyAppearance;
        return this;
    }

    void resolveCrossLodReferences(AbstractFeature feature) {
        ReferenceCollector collector = new ReferenceCollector();
        Map<Integer, Map<AbstractGeometry, List<GeometryProperty<?>>>> references = collector.collect(feature);
        if (references.isEmpty()) {
            return;
        }

        if (mode == Mode.RESOLVE) {
            GeometryCopier geometryCopier = new GeometryCopier()
                    .copyAppearance(copyAppearance)
                    .withAppearanceHelper(new AppearanceHelper(feature));
            for (Map<AbstractGeometry, List<GeometryProperty<?>>> lod : references.values()) {
                try (CopySession session = geometryCopier.createSession()) {
                    for (Map.Entry<AbstractGeometry, List<GeometryProperty<?>>> entry : lod.entrySet()) {
                        boolean inline = !session.hasClone(entry.getKey());
                        AbstractGeometry clone = geometryCopier.copy(entry.getKey(), feature, session);

                        GeometryProperty<?> targetProperty = entry.getValue().get(0);
                        if (inline) {
                            targetProperty.setInlineObjectIfValid(clone);
                            targetProperty.setHref(null);
                        } else {
                            targetProperty.setReferencedObjectIfValid(clone);
                            targetProperty.setHref("#" + clone.getId());
                        }

                        if (entry.getValue().size() > 1) {
                            entry.getValue().stream()
                                    .skip(1)
                                    .forEach(property -> {
                                        property.setReferencedObjectIfValid(clone);
                                        property.setHref("#" + clone.getId());
                                    });
                        }
                    }
                }
            }
        } else {
            for (Map<AbstractGeometry, List<GeometryProperty<?>>> lod : references.values()) {
                for (List<GeometryProperty<?>> properties : lod.values()) {
                    for (GeometryProperty<?> property : properties) {
                        Child parent = property.getParent();
                        if (parent instanceof GMLObject object) {
                            object.unsetProperty(property);
                        }
                    }
                }
            }
        }
    }

    private class ReferenceCollector extends ObjectWalker {
        private final Map<Integer, Map<AbstractGeometry, List<GeometryProperty<?>>>> references = new HashMap<>();
        private final Map<GeometryProperty<?>, Integer> properties = new IdentityHashMap<>();

        Map<Integer, Map<AbstractGeometry, List<GeometryProperty<?>>>> collect(AbstractFeature feature) {
            GeometryInfo geometryInfo = feature.getGeometryInfo(true);
            for (int lod : geometryInfo.getLods()) {
                for (GeometryProperty<?> property : geometryInfo.getGeometries(lod)) {
                    properties.put(property, lod);
                }
            }

            feature.accept(this);
            return references;
        }

        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
        }

        @Override
        public void visit(GeometryProperty<?> property) {
            if (property.isSetReferencedObject() && property.getHref() != null) {
                Integer lod = getLod(property);
                if (lod == null) {
                    return;
                }

                Integer targetLod = getLod(property.getObject().getParent(GeometryProperty.class));
                if (targetLod != null && !lod.equals(targetLod)) {
                    if (mode == Mode.RESOLVE
                            || (mode == Mode.REMOVE_LOD4_REFERENCES && targetLod == 4)) {
                        references.computeIfAbsent(lod, k -> new LinkedHashMap<>())
                                .computeIfAbsent(property.getObject(), k -> new ArrayList<>())
                                .add(property);
                    }
                }
            } else {
                super.visit(property);
            }
        }

        private Integer getLod(GeometryProperty<?> property) {
            Integer lod;
            do {
                lod = properties.get(property);
            } while (lod == null && (property = property.getParent(GeometryProperty.class)) != null);

            return lod;
        }
    }
}
