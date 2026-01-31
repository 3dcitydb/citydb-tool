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

package org.citydb.io.citygml.writer.preprocess;

import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.property.GeometryProperty;
import org.citydb.model.property.ImplicitGeometryProperty;
import org.citydb.model.walker.ModelWalker;

import java.util.HashMap;
import java.util.Map;

public class Preprocessor {
    private final Processor processor = new Processor();
    private final Map<String, ImplicitGeometry> implicitGeometries = new HashMap<>();

    private boolean checkForDeprecatedLod4Geometry;
    private boolean hasDeprecatedLod4Geometry;

    public Preprocessor checkForDeprecatedLod4Geometry(boolean checkForDeprecatedLod4Geometry) {
        this.checkForDeprecatedLod4Geometry = checkForDeprecatedLod4Geometry;
        return this;
    }

    public void process(Feature feature) {
        feature.accept(processor);
    }

    public boolean hasDeprecatedLod4Geometry() {
        return hasDeprecatedLod4Geometry;
    }

    public ImplicitGeometry lookupImplicitGeometry(String objectId) {
        return implicitGeometries.get(objectId);
    }

    public void clear() {
        hasDeprecatedLod4Geometry = false;
        implicitGeometries.clear();
    }

    private class Processor extends ModelWalker {

        @Override
        public void visit(GeometryProperty property) {
            if (checkForDeprecatedLod4Geometry
                    && !hasDeprecatedLod4Geometry
                    && property.getLod().orElse("").equals("4")
                    && property.getName().getNamespace().equals(Namespaces.DEPRECATED)) {
                hasDeprecatedLod4Geometry = true;
            }
        }

        @Override
        public void visit(ImplicitGeometryProperty property) {
            if (checkForDeprecatedLod4Geometry
                    && !hasDeprecatedLod4Geometry
                    && property.getLod().orElse("").equals("4")
                    && property.getName().getNamespace().equals(Namespaces.DEPRECATED)) {
                hasDeprecatedLod4Geometry = true;
            }

            property.getObject().ifPresent(implicitGeometry ->
                    implicitGeometries.put(implicitGeometry.getOrCreateObjectId(), implicitGeometry));
        }
    }
}
