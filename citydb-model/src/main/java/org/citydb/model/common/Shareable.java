/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.common;

import java.util.Optional;

public abstract class Shareable extends Child {

    @Override
    public Optional<Child> getParent() {
        return Optional.empty();
    }

    @Override
    void setParent(Child parent) {
        // do nothing
    }
}
