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

package org.citydb.io.citygml.writer.preprocess;

import org.citydb.model.appearance.Texture;
import org.citydb.model.appearance.TextureImageProperty;
import org.citydb.model.common.ExternalFile;
import org.citydb.model.common.Namespaces;
import org.citydb.model.feature.Feature;
import org.citydb.model.property.GeometryProperty;
import org.citydb.model.property.ImplicitGeometryProperty;
import org.citydb.model.walker.ModelWalker;

import java.util.HashMap;
import java.util.Map;

public class Preprocessor {
    private final Processor processor = new Processor();
    private final Map<String, ExternalFile> externalFiles = new HashMap<>();

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

    public ExternalFile lookupExternalFile(String objectId) {
        return externalFiles.get(objectId);
    }

    public void clear() {
        hasDeprecatedLod4Geometry = false;
        externalFiles.clear();
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
        }

        @Override
        public void visit(Texture<?> texture) {
            texture.getTextureImageProperty()
                    .flatMap(TextureImageProperty::getObject)
                    .ifPresent(externalFile -> externalFiles.put(externalFile.getOrCreateObjectId(), externalFile));
        }
    }
}
