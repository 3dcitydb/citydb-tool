/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.importer.util;

import org.citydb.model.feature.Feature;
import org.citydb.model.feature.FeatureDescriptor;

public class ImportLogEntry {
    private final int objectClassId;
    private final String objectId;
    private final long databaseId;
    private boolean committed;

    private ImportLogEntry(Feature feature, FeatureDescriptor descriptor) {
        objectClassId = descriptor.getObjectClassId();
        objectId = feature.getOrCreateObjectId();
        databaseId = descriptor.getId();
    }

    public static ImportLogEntry of(Feature feature, FeatureDescriptor descriptor) {
        return new ImportLogEntry(feature, descriptor);
    }

    public int getObjectClassId() {
        return objectClassId;
    }

    public String getObjectId() {
        return objectId;
    }

    public long getDatabaseId() {
        return databaseId;
    }

    public boolean isCommitted() {
        return committed;
    }

    public ImportLogEntry setCommitted(boolean committed) {
        this.committed = committed;
        return this;
    }
}
