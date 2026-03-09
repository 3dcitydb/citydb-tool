/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.common;

import org.citydb.model.address.Address;
import org.citydb.model.appearance.Appearance;
import org.citydb.model.appearance.GeoreferencedTexture;
import org.citydb.model.appearance.ParameterizedTexture;
import org.citydb.model.appearance.X3DMaterial;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.*;

public interface Visitor {
    void visit(Address address);

    void visit(Appearance appearance);

    void visit(CompositeSolid compositeSolid);

    void visit(CompositeSurface compositeSurface);

    void visit(Feature feature);

    void visit(GeoreferencedTexture texture);

    void visit(ImplicitGeometry implicitGeometry);

    void visit(LineString lineString);

    void visit(MultiLineString multiLineString);

    void visit(MultiPoint multiPoint);

    void visit(MultiSolid multiSolid);

    void visit(MultiSurface multiSurface);

    void visit(ParameterizedTexture texture);

    void visit(Point point);

    void visit(Polygon polygon);

    void visit(Solid solid);

    void visit(TriangulatedSurface triangulatedSurface);

    void visit(X3DMaterial material);
}
