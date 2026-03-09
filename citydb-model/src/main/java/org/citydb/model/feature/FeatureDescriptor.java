/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.model.feature;

import org.citydb.model.common.DatabaseDescriptor;

public class FeatureDescriptor extends DatabaseDescriptor {
    private final int objectClassId;
    private long sequenceId;

    private FeatureDescriptor(long id, int objectClassId) {
        super(id);
        this.objectClassId = objectClassId;
    }

    public static FeatureDescriptor of(long id, int objectClassId) {
        return new FeatureDescriptor(id, objectClassId);
    }

    public int getObjectClassId() {
        return objectClassId;
    }

    public long getSequenceId() {
        return sequenceId;
    }

    public FeatureDescriptor setSequenceId(long sequenceId) {
        this.sequenceId = sequenceId;
        return this;
    }
}
