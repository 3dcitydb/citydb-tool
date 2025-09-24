/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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
