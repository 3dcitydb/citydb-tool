/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
