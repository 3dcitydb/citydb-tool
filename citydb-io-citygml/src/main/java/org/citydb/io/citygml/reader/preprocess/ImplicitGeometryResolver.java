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
import org.xmlobjects.gml.model.geometry.GeometryProperty;
import org.xmlobjects.util.copy.CopyBuilder;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ImplicitGeometryResolver {
    private final CopyBuilder copyBuilder;
    private final Map<String, ImplicitGeometry> implicitGeometries = new ConcurrentHashMap<>();
    private final ResolverProcessor processor = new ResolverProcessor();

    ImplicitGeometryResolver(CopyBuilder copyBuilder) {
        this.copyBuilder = copyBuilder;
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
            if (implicitGeometry.getRelativeGeometry() != null) {
                GeometryProperty<?> property = implicitGeometry.getRelativeGeometry();
                if (property.isSetInlineObject() && property.getObject().getId() != null) {
                    ImplicitGeometry template = implicitGeometries.get(property.getObject().getId());
                    if (template != null) {
                        property.setInlineObjectIfValid(template.getRelativeGeometry().getObject());
                        implicitGeometry.setAppearances(template.getAppearances());
                    }
                } else if (property.getHref() != null) {
                    ImplicitGeometry template = implicitGeometries.get(FeatureHelper.getIdFromReference(
                            property.getHref()));
                    if (template != null) {
                        property.setReferencedObjectIfValid(template.getRelativeGeometry().getObject());
                        implicitGeometry.setAppearances(template.getAppearances().stream()
                                .map(copyBuilder::shallowCopy)
                                .toList());
                    }
                }
            }
        }
    }
}
