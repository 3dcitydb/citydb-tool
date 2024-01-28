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

import org.citydb.database.schema.Sequence;

import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;

public class SequenceValues {
    private final Map<Sequence, Deque<Long>> values = new EnumMap<>(Sequence.class);

    SequenceValues() {
    }

    void addValue(Sequence sequence, long value) {
        values.computeIfAbsent(sequence, v -> new ArrayDeque<>()).add(value);
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
}
