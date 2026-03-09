/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.importer.reference;

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

    public void putReference(String reference, long id) {
        if (reference != null) {
            references.put(id, reference);
        }
    }

    public void clear() {
        targets.clear();
        references.clear();
    }
}
