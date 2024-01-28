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

package org.citydb.operation.importer.reference;

import org.citydb.model.common.Reference;

import java.util.HashMap;
import java.util.Map;

public class ReferenceCache {
    private final CacheType type;
    private final Map<String, Long> targets = new HashMap<>();
    private final Map<Long, String> references = new HashMap<>();

    public ReferenceCache(CacheType type) {
        this.type = type;
    }

    public CacheType getType() {
        return type;
    }

    Map<String, Long> getTargets() {
        return targets;
    }

    Map<Long, String> getReferences() {
        return references;
    }

    public void putTarget(String objectId, long id) {
        if (objectId != null) {
            targets.put(objectId, id);
        }
    }

    public void putReference(Reference reference, long id) {
        if (reference != null) {
            references.put(id, reference.getTarget());
        }
    }

    public void clear() {
        targets.clear();
        references.clear();
    }
}
