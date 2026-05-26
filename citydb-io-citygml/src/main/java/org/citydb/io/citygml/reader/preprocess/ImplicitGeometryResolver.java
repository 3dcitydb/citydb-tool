/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.reader.preprocess;

import org.citydb.io.citygml.reader.util.FeatureHelper;
import org.citygml4j.core.model.core.AbstractAppearanceProperty;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.core.visitor.ObjectWalker;
import org.xmlobjects.copy.Copier;
import org.xmlobjects.gml.model.geometry.GeometryProperty;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ImplicitGeometryResolver {
    private final Copier copier;
    private final Map<String, ImplicitGeometry> implicitGeometries = new ConcurrentHashMap<>();
    private final ResolverProcessor processor = new ResolverProcessor();

    ImplicitGeometryResolver(Copier copier) {
        this.copier = copier;
    }

    boolean hasImplicitGeometries() {
        return !implicitGeometries.isEmpty();
    }

    Collection<ImplicitGeometry> getImplicitGeometries() {
        return implicitGeometries.values();
    }

    void addImplicitGeometry(ImplicitGeometry implicitGeometry) {
        if (implicitGeometry.getRelativeGeometry() != null
                && implicitGeometry.getRelativeGeometry().isSetInlineObject()
                && implicitGeometry.getRelativeGeometry().getObject().getId() != null) {
            ImplicitGeometry template = new ImplicitGeometry(implicitGeometry.getRelativeGeometry());
            if (implicitGeometry.isSetAppearances()) {
                implicitGeometry.getAppearances().stream()
                        .filter(AbstractAppearanceProperty::isSetInlineObject)
                        .forEach(template.getAppearances()::add);
            }

            implicitGeometries.put(implicitGeometry.getRelativeGeometry().getObject().getId(), template);
        }
    }

    void resolveImplicitGeometries(AbstractFeature feature) {
        feature.accept(processor);
    }

    private class ResolverProcessor extends ObjectWalker {
        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
            GeometryProperty<?> property = implicitGeometry.getRelativeGeometry();
            if (property != null) {
                boolean inline = property.isSetInlineObject() && property.getObject().getId() != null;
                String id = inline
                        ? property.getObject().getId()
                        : FeatureHelper.getIdFromReference(property.getHref());

                ImplicitGeometry template = id != null ? implicitGeometries.get(id) : null;
                if (template != null) {
                    if (inline) {
                        property.setInlineObjectIfValid(template.getRelativeGeometry().getObject());
                        if (template.isSetAppearances()) {
                            implicitGeometry.setAppearances(template.getAppearances());
                        }
                    } else {
                        property.setReferencedObjectIfValid(template.getRelativeGeometry().getObject());
                        if (template.isSetAppearances()) {
                            implicitGeometry.setAppearances(template.getAppearances().stream()
                                    .map(copier::shallowCopy)
                                    .toList());
                        }
                    }
                }
            }
        }
    }
}
