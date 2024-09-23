/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
 * virtualcitysystems GmbH, Germany
 * https://vc.systems/
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
