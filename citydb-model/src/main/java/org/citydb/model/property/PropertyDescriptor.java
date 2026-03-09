/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.property;

import org.citydb.model.common.DatabaseDescriptor;

public class PropertyDescriptor extends DatabaseDescriptor {
    private final long featureId;
    private long parentId;

    private PropertyDescriptor(long id, long featureId) {
        super(id);
        this.featureId = featureId;
    }

    public static PropertyDescriptor of(long id, long featureId) {
        return new PropertyDescriptor(id, featureId);
    }

    public long getFeatureId() {
        return featureId;
    }

    public PropertyDescriptor setParentId(long parentId) {
        this.parentId = parentId;
        return this;
    }

    public long getParentId() {
        return parentId;
    }
}
