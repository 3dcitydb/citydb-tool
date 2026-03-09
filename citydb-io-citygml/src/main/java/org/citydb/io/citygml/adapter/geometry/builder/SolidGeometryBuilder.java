/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.geometry.builder;

import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.model.geometry.*;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.visitor.GeometryWalker;

import java.util.ArrayList;
import java.util.List;

public class SolidGeometryBuilder extends GeometryBuilder {
    private final SurfaceGeometryBuilder builder;
    private final SolidProcessor processor = new SolidProcessor();

    SolidGeometryBuilder(SurfaceGeometryBuilder builder) {
        this.builder = builder;
    }

    @Override
    Geometry<?> build(AbstractGeometry source) throws ModelBuildException {
        try {
            source.accept(processor);
            if (processor.exception != null) {
                throw processor.exception;
            }

            if (!processor.solids.isEmpty()) {
                if (source instanceof org.xmlobjects.gml.model.geometry.aggregates.MultiSolid) {
                    return MultiSolid.of(processor.solids);
                } else if (source instanceof org.xmlobjects.gml.model.geometry.complexes.CompositeSolid) {
                    return CompositeSolid.of(processor.solids);
                } else {
                    return processor.solids.size() == 1 ?
                            processor.solids.get(0) :
                            MultiSolid.of(processor.solids);
                }
            } else {
                return null;
            }
        } finally {
            processor.solids.clear();
            processor.exception = null;
        }
    }

    private class SolidProcessor extends GeometryWalker {
        private final List<Solid> solids = new ArrayList<>();
        private ModelBuildException exception;

        @Override
        public void visit(org.xmlobjects.gml.model.geometry.primitives.Solid solid) {
            if (solid.getExterior() != null && solid.getExterior().getObject() != null) {
                try {
                    Geometry<?> geometry = builder.build(solid.getExterior().getObject());
                    if (geometry instanceof CompositeSurface shell) {
                        solids.add(Solid.of(shell)
                                .setObjectId(solid.getId()));
                    }
                } catch (ModelBuildException e) {
                    exception = e;
                    setShouldWalk(false);
                }
            }
        }
    }
}
