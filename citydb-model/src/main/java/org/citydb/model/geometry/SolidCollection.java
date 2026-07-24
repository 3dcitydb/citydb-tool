/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.geometry;

import org.citydb.model.common.Child;
import org.citydb.model.common.ChildList;
import org.citydb.model.util.CopySession;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public abstract class SolidCollection<T extends SolidCollection<?>> extends Geometry<T> {
    private final List<Solid> solids;

    SolidCollection(int initialCapacity) {
        solids = new ChildList<>(initialCapacity, this);
    }

    SolidCollection(List<Solid> solids) {
        Objects.requireNonNull(solids, "The solid list must not be null.");
        this.solids = asChild(solids);
    }

    SolidCollection(Solid... solids) {
        Objects.requireNonNull(solids, "The solid array must not be null.");
        this.solids = asChild(Arrays.asList(solids));
    }

    public List<Solid> getSolids() {
        return solids;
    }

    @Override
    public int getVertexDimension() {
        return 3;
    }

    @Override
    protected void copyPropertiesTo(Child clone, CopySession session) {
        SolidCollection<?> collection = (SolidCollection<?>) clone;
        super.copyPropertiesTo(collection, session);

        for (Solid solid : solids) {
            collection.solids.add(copy(solid, session));
        }
    }
}
