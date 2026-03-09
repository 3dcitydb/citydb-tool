/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.common;

import java.io.Serializable;

public abstract class DatabaseDescriptor implements Serializable {
    private final long id;

    public DatabaseDescriptor(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }
}
