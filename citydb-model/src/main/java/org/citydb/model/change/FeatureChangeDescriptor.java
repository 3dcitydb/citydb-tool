/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.change;

import org.citydb.model.common.DatabaseDescriptor;

import java.util.Optional;

public class FeatureChangeDescriptor extends DatabaseDescriptor {
    private final int objectClassId;
    private final Long featureId;

    private FeatureChangeDescriptor(long id, int objectClassId, Long featureId) {
        super(id);
        this.objectClassId = objectClassId;
        this.featureId = featureId;
    }

    public static FeatureChangeDescriptor of(long id, int objectClassId, Long featureId) {
        return new FeatureChangeDescriptor(id, objectClassId, featureId);
    }

    public int getObjectClassId() {
        return objectClassId;
    }

    public Optional<Long> getFeatureId() {
        return Optional.ofNullable(featureId);
    }
}
