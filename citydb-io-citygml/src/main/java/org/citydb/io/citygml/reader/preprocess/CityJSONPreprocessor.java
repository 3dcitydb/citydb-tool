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
        ThreadLocal<CopyBuilder> copyBuilders = ThreadLocal.withInitial(() ->
                new CopyBuilder().failOnError(true));

        implicitGeometryResolver = new ImplicitGeometryResolver(copyBuilders::get, referenceResolver);
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
