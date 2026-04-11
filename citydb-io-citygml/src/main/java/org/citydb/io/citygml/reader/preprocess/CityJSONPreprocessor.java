/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.reader.preprocess;

import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.core.util.reference.DefaultReferenceResolver;
import org.citygml4j.core.visitor.ObjectWalker;
import org.xmlobjects.copy.Copier;
import org.xmlobjects.copy.CopierBuilder;
import org.xmlobjects.gml.util.reference.ReferenceResolver;

public class CityJSONPreprocessor {
    private final ImplicitGeometryResolver implicitGeometryResolver;
    private final ReferenceResolver referenceResolver = DefaultReferenceResolver.newInstance();
    private final ImplicitGeometryCollector collector = new ImplicitGeometryCollector();

    public CityJSONPreprocessor() {
        Copier copier = CopierBuilder.newCopier();
        implicitGeometryResolver = new ImplicitGeometryResolver(copier);
    }

    public void processGlobalObjects(AbstractFeature abstractFeature) {
        abstractFeature.accept(collector);
    }

    public void process(AbstractFeature feature) {
        referenceResolver.resolveReferences(feature);
        implicitGeometryResolver.resolveImplicitGeometries(feature);
    }

    private class ImplicitGeometryCollector extends ObjectWalker {
        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
            implicitGeometryResolver.addImplicitGeometry(implicitGeometry);
        }
    }
}
