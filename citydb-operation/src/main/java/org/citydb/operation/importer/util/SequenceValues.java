/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.importer.util;

import org.citydb.database.schema.Sequence;
import org.citydb.model.common.Referencable;
import org.citydb.operation.importer.reference.CacheType;

import java.sql.SQLException;
import java.util.*;

public class SequenceValues {
    private final Map<CacheType, Set<String>> idCache;
    private final Map<Sequence, Deque<Long>> values = new EnumMap<>(Sequence.class);

    SequenceValues(Map<CacheType, Set<String>> idCache) {
        this.idCache = idCache;
    }

    void addValues(Sequence sequence, List<Long> values) {
        this.values.computeIfAbsent(sequence, v -> new ArrayDeque<>(values));
    }

    public boolean hasNext(Sequence sequence) {
        Deque<Long> values = this.values.get(sequence);
        return values != null && !values.isEmpty();
    }

    public long next(Sequence sequence) throws SQLException {
        Deque<Long> values = this.values.get(sequence);
        if (values != null && !values.isEmpty()) {
            return values.pop();
        } else {
            throw new SQLException("No more values available for sequence " + sequence + ".");
        }
    }

    public boolean hasValueFor(CacheType type, Referencable object) {
        Set<String> ids = idCache.get(type);
        if (ids != null && !ids.isEmpty()) {
            String objectId = object.getObjectId().orElse(null);
            return objectId == null || ids.remove(objectId);
        } else {
            return false;
        }
    }
}
