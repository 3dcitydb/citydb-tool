/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.geometry;

import org.citydb.model.common.Visitor;
import org.citydb.model.util.CopySession;

import java.util.Collections;
import java.util.List;

public class MultiSolid extends SolidCollection<MultiSolid> {

    private MultiSolid(int initialCapacity) {
        super(initialCapacity);
    }

    private MultiSolid(List<Solid> solids) {
        super(solids);
    }

    private MultiSolid(Solid... solids) {
        super(solids);
    }

    public static MultiSolid of(List<Solid> solids) {
        return new MultiSolid(solids);
    }

    public static MultiSolid of(Solid... solids) {
        return new MultiSolid(solids);
    }

    public static MultiSolid empty() {
        return new MultiSolid(Collections.emptyList());
    }

    @Override
    protected MultiSolid createClone(CopySession session) {
        return new MultiSolid(getSolids().size());
    }

    @Override
    public GeometryType getGeometryType() {
        return GeometryType.MULTI_SOLID;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    MultiSolid self() {
        return this;
    }
}
