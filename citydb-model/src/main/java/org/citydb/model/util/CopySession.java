/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.util;

import org.citydb.model.common.Child;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

public class CopySession implements AutoCloseable {
    private final Map<Child, Child> clones = new IdentityHashMap<>();

    public <T extends Child> T lookupClone(Child src, Class<T> type) {
        Object clone = clones.get(src);
        return type.isInstance(clone) ? type.cast(clone) : null;
    }

    public void registerClone(Child src, Child clone) {
        clones.put(Objects.requireNonNull(src), Objects.requireNonNull(clone));
    }

    @Override
    public void close() {
        clones.clear();
    }
}
