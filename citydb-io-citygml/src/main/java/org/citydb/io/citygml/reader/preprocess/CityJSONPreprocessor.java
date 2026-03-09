/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.reader.preprocess;

import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.core.util.reference.DefaultReferenceResolver;
import org.citygml4j.core.visitor.ObjectWalker;
import org.xmlobjects.gml.util.reference.ReferenceResolver;
import org.xmlobjects.util.copy.CopyBuilder;

public class CityJSONPreprocessor {
    private final ImplicitGeometryResolver implicitGeometryResolver;
    private final ReferenceResolver referenceResolver = DefaultReferenceResolver.newInstance();
    private final ImplicitGeometryCollector collector = new ImplicitGeometryCollector();

    public CityJSONPreprocessor() {
        CopyBuilder copyBuilder = new CopyBuilder();
        implicitGeometryResolver = new ImplicitGeometryResolver(copyBuilder);
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
