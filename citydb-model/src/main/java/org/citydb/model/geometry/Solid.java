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

package org.citydb.model.geometry;

import org.citydb.model.common.Visitor;

import java.util.Objects;

public class Solid extends Geometry<Solid> {
    private final CompositeSurface shell;

    private Solid(CompositeSurface shell) {
        Objects.requireNonNull(shell, "The exterior shell must not be null.");
        if (shell.getVertexDimension() != 3) {
            throw new IllegalArgumentException("The vertex dimension of the exterior shell must be 3.");
        }

        this.shell = asChild(shell);
    }

    public static Solid of(CompositeSurface shell) {
        return new Solid(shell);
    }

    public static Solid empty() {
        return new Solid(CompositeSurface.empty());
    }

    public CompositeSurface getShell() {
        return shell;
    }

    @Override
    public int getVertexDimension() {
        return 3;
    }

    @Override
    public Solid copy() {
        return new Solid(shell.copy())
                .copyPropertiesFrom(this);
    }

    @Override
    public GeometryType getGeometryType() {
        return GeometryType.SOLID;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    Solid self() {
        return this;
    }
}
