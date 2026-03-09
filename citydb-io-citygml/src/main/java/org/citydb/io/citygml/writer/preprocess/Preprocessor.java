/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
