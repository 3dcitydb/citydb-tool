/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.geometry;

import org.citydb.model.common.DatabaseDescriptor;

public class GeometryDescriptor extends DatabaseDescriptor {
    private final long featureId;

    private GeometryDescriptor(long id, long featureId) {
        super(id);
        this.featureId = featureId;
    }

    public static GeometryDescriptor of(long id, long featureId) {
        return new GeometryDescriptor(id, featureId);
    }

    public long getFeatureId() {
        return featureId;
    }
}
