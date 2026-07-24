/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.geometry;

import org.citydb.model.common.Visitor;
import org.citydb.model.util.CopySession;

import java.util.Collections;
import java.util.List;

public class CompositeSolid extends SolidCollection<CompositeSolid> {

    private CompositeSolid(int initialCapacity) {
        super(initialCapacity);
    }

    private CompositeSolid(List<Solid> solids) {
        super(solids);
    }

    private CompositeSolid(Solid... solids) {
        super(solids);
    }

    public static CompositeSolid of(List<Solid> solids) {
        return new CompositeSolid(solids);
    }

    public static CompositeSolid of(Solid... solids) {
        return new CompositeSolid(solids);
    }

    public static CompositeSolid empty() {
        return new CompositeSolid(Collections.emptyList());
    }

    @Override
    protected CompositeSolid createClone(CopySession session) {
        return new CompositeSolid(getSolids().size());
    }

    @Override
    public GeometryType getGeometryType() {
        return GeometryType.COMPOSITE_SOLID;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    CompositeSolid self() {
        return this;
    }
}
