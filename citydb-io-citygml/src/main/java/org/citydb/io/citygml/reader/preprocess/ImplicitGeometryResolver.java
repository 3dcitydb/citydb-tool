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
import org.citygml4j.core.model.core.AbstractAppearanceProperty;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.core.visitor.ObjectWalker;
import org.xmlobjects.gml.model.geometry.GeometryProperty;
import org.xmlobjects.gml.util.reference.ReferenceResolver;
import org.xmlobjects.util.copy.CopyBuilder;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ImplicitGeometryResolver {
    private final CopyBuilder copyBuilder;
    private final ReferenceResolver referenceResolver;
    private final Map<String, ImplicitGeometry> implicitGeometries = new ConcurrentHashMap<>();
    private final ResolverProcessor processor = new ResolverProcessor();

    ImplicitGeometryResolver(CopyBuilder copyBuilder, ReferenceResolver referenceResolver) {
        this.copyBuilder = copyBuilder;
        this.referenceResolver = referenceResolver;
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
                        implicitGeometry.setRelativeGeometry(template.getRelativeGeometry());
                        implicitGeometry.setAppearances(template.getAppearances());
                    }
                } else if (property.getHref() != null) {
                    ImplicitGeometry template = implicitGeometries.get(FeatureHelper.getIdFromReference(
                            property.getHref()));
                    if (template != null) {
                        property.setReferencedObjectIfValid(template.getRelativeGeometry().getObject());
                        if (implicitGeometry.isSetAppearances()) {
                            implicitGeometry.getAppearances().removeIf(p -> !p.isSetInlineObject());
                            referenceResolver.resolveReferences(implicitGeometry);
                        }

                        if (template.isSetAppearances()) {
                            template.getAppearances().forEach(p -> implicitGeometry.getAppearances().add(
                                    copyBuilder.shallowCopy(p)));
                        }
                    }
                }
            }
        }
    }
}
