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

package org.citydb.model.walker;

import org.citydb.model.address.Address;
import org.citydb.model.appearance.*;
import org.citydb.model.common.InlineOrByReferenceProperty;
import org.citydb.model.common.InlineProperty;
import org.citydb.model.common.Visitable;
import org.citydb.model.common.Visitor;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.*;
import org.citydb.model.property.*;

public class ModelWalker implements Visitor {
    private boolean shouldWalk = true;

    public boolean shouldWalk() {
        return shouldWalk;
    }

    public void setShouldWalk(boolean shouldWalk) {
        this.shouldWalk = shouldWalk;
    }

    public void reset() {
        shouldWalk = true;
    }

    public void visit(Geometry<?> geometry) {
    }

    public void visit(Property<?> property) {
    }

    public void visit(Surface<?> surface) {
        visit((Geometry<?>) surface);
    }

    public void visit(SurfaceData<?> surfaceData) {
    }

    public void visit(Texture<?> texture) {
        visit((SurfaceData<?>) texture);
    }

    public void visit(SolidCollection<?> collection) {
        visit((Geometry<?>) collection);

        for (Solid solid : collection.getSolids()) {
            if (shouldWalk) {
                solid.accept(this);
            }
        }
    }

    public void visit(SurfaceCollection<?> collection) {
        visit((Surface<?>) collection);

        for (Polygon polygon : collection.getPolygons()) {
            if (shouldWalk) {
                polygon.accept(this);
            }
        }
    }

    @Override
    public void visit(Address address) {
        if (shouldWalk) {
            address.getMultiPoint().ifPresent(multiPoint -> multiPoint.accept(this));
        }
    }

    @Override
    public void visit(Appearance appearance) {
        if (appearance.hasSurfaceData()) {
            for (SurfaceDataProperty property : appearance.getSurfaceData()) {
                visit(property);
            }
        }
    }

    @Override
    public void visit(CompositeSolid compositeSolid) {
        visit((SolidCollection<?>) compositeSolid);
    }

    @Override
    public void visit(CompositeSurface compositeSurface) {
        visit((SurfaceCollection<?>) compositeSurface);
    }

    @Override
    public void visit(Feature feature) {
        if (feature.hasFeatures()) {
            for (FeatureProperty property : feature.getFeatures().getAll()) {
                visit(property);
            }
        }

        if (feature.hasGeometries()) {
            for (GeometryProperty property : feature.getGeometries().getAll()) {
                visit(property);
            }
        }

        if (feature.hasImplicitGeometries()) {
            for (ImplicitGeometryProperty property : feature.getImplicitGeometries().getAll()) {
                visit(property);
            }
        }

        if (feature.hasAppearances()) {
            for (AppearanceProperty property : feature.getAppearances().getAll()) {
                visit(property);
            }
        }

        if (feature.hasAddresses()) {
            for (AddressProperty property : feature.getAddresses().getAll()) {
                visit(property);
            }
        }

        if (feature.hasAttributes()) {
            for (Attribute attribute : feature.getAttributes().getAll()) {
                visit(attribute);
            }
        }
    }

    @Override
    public void visit(GeoreferencedTexture texture) {
        visit((Texture<?>) texture);

        if (shouldWalk) {
            texture.getReferencePoint().ifPresent(referencePoint -> referencePoint.accept(this));
        }
    }

    @Override
    public void visit(ImplicitGeometry implicitGeometry) {
        if (shouldWalk) {
            implicitGeometry.getGeometry().ifPresent(geometry -> geometry.accept(this));
        }

        if (implicitGeometry.hasAppearances()) {
            for (AppearanceProperty property : implicitGeometry.getAppearances().getAll()) {
                visit(property);
            }
        }
    }

    @Override
    public void visit(LineString lineString) {
        visit((Geometry<?>) lineString);
    }

    @Override
    public void visit(MultiLineString multiLineString) {
        visit((Geometry<?>) multiLineString);

        for (LineString lineString : multiLineString.getLineStrings()) {
            if (shouldWalk) {
                lineString.accept(this);
            }
        }
    }

    @Override
    public void visit(MultiPoint multiPoint) {
        visit((Geometry<?>) multiPoint);

        for (Point point : multiPoint.getPoints()) {
            if (shouldWalk) {
                point.accept(this);
            }
        }
    }

    @Override
    public void visit(MultiSolid multiSolid) {
        visit((SolidCollection<?>) multiSolid);
    }

    @Override
    public void visit(MultiSurface multiSurface) {
        visit((SurfaceCollection<?>) multiSurface);
    }

    @Override
    public void visit(ParameterizedTexture texture) {
        visit((Texture<?>) texture);
    }

    @Override
    public void visit(Point point) {
        visit((Geometry<?>) point);
    }

    @Override
    public void visit(Polygon polygon) {
        visit((Surface<?>) polygon);
    }

    @Override
    public void visit(Solid solid) {
        visit((Geometry<?>) solid);

        if (shouldWalk) {
            visit(solid.getShell());
        }
    }

    @Override
    public void visit(TriangulatedSurface triangulatedSurface) {
        visit((SurfaceCollection<?>) triangulatedSurface);
    }

    @Override
    public void visit(X3DMaterial material) {
        visit((SurfaceData<?>) material);
    }

    public void visit(InlineProperty<?> property) {
        if (property instanceof Property<?> object) {
            visit(object);
        }

        if (shouldWalk) {
            visitObject(property.getObject());
        }
    }

    public void visit(InlineOrByReferenceProperty<?> property) {
        if (property instanceof Property<?> object) {
            visit(object);
        }

        if (shouldWalk) {
            visitObject(property.getObject().orElse(null));
        }
    }

    public void visit(AppearanceProperty property) {
        visit((InlineProperty<?>) property);
    }

    public void visit(AddressProperty property) {
        visit((InlineOrByReferenceProperty<?>) property);
    }

    public void visit(FeatureProperty property) {
        visit((InlineOrByReferenceProperty<?>) property);
    }

    public void visit(GeometryProperty property) {
        visit((InlineProperty<?>) property);
    }

    public void visit(SurfaceDataProperty property) {
        visit((InlineOrByReferenceProperty<?>) property);
    }

    public void visit(ImplicitGeometryProperty property) {
        visit((InlineOrByReferenceProperty<?>) property);

        if (shouldWalk) {
            property.getReferencePoint().ifPresent(referencePoint -> referencePoint.accept(this));
        }
    }

    private void visitObject(Object object) {
        if (object instanceof Visitable visitable) {
            visitable.accept(this);
        }
    }

    public void visit(Attribute attribute) {
        visit((Property<?>) attribute);

        if (attribute.hasProperties()) {
            for (Property<?> child : attribute.getProperties().getAll()) {
                if (child instanceof FeatureProperty property) {
                    visit(property);
                } else if (child instanceof GeometryProperty property) {
                    visit(property);
                } else if (child instanceof AppearanceProperty property) {
                    visit(property);
                } else if (child instanceof ImplicitGeometryProperty property) {
                    visit(property);
                } else if (child instanceof Attribute childAttribute) {
                    visit(childAttribute);
                }
            }
        }
    }
}
