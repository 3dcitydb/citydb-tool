/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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
