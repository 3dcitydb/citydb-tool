/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.core.visitor.ObjectWalker;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.GeometryProperty;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ImplicitGeometryResolver {
    private final Map<String, AbstractGeometry> geometries = new ConcurrentHashMap<>();
    private final ResolverProcessor processor = new ResolverProcessor();

    ImplicitGeometryResolver() {
    }

    void addImplicitGeometry(AbstractGeometry geometry) {
        if (geometry.getId() != null) {
            geometries.put(geometry.getId(), geometry);
        }
    }

    void resolveImplicitGeometries(AbstractFeature feature) {
        feature.accept(processor);
    }

    private class ResolverProcessor extends ObjectWalker {
        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
            if (implicitGeometry.getRelativeGeometry() != null
                    && implicitGeometry.getRelativeGeometry().getObject() == null
                    && implicitGeometry.getRelativeGeometry().getHref() != null) {
                GeometryProperty<?> property = implicitGeometry.getRelativeGeometry();
                AbstractGeometry template = geometries.get(FeatureHelper.getIdFromReference(property.getHref()));
                if (template != null) {
                    property.setReferencedObjectIfValid(template);
                }
            }
        }
    }
}
