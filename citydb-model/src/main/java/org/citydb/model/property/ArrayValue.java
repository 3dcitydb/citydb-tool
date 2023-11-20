/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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

package org.citydb.model.property;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class ArrayValue implements Serializable {
    private final List<Value> values;

    private ArrayValue(List<Value> values) {
        this.values = Objects.requireNonNull(values, "The value list must not be null.");
    }

    public static ArrayValue newInstance() {
        return new ArrayValue(new ArrayList<>());
    }

    public static ArrayValue of(List<Value> values) {
        return new ArrayValue(values);
    }

    public static ArrayValue ofBoolean(List<Boolean> values) {
        return ofList(Objects.requireNonNull(values, "The boolean list must not be null."));
    }

    public static ArrayValue ofInt(List<Integer> values) {
        return ofList(Objects.requireNonNull(values, "The integer list must not be null."));
    }

    public static ArrayValue ofLong(List<Long> values) {
        return ofList(Objects.requireNonNull(values, "The long list must not be null."));
    }

    public static ArrayValue ofDouble(List<Double> values) {
        return ofList(Objects.requireNonNull(values, "The double list must not be null."));
    }

    public static ArrayValue ofString(List<String> values) {
        return ofList(Objects.requireNonNull(values, "The string list must not be null."));
    }

    public static ArrayValue ofList(List<?> values) {
        ArrayValue arrayValue = newInstance();
        for (Object value : values) {
            if (value instanceof Boolean) {
                arrayValue.add(Value.of((Boolean) value));
            } else if (value instanceof Integer) {
                arrayValue.add(Value.of((Integer) value));
            } else if (value instanceof Long) {
                arrayValue.add(Value.of((Long) value));
            } else if (value instanceof Double) {
                arrayValue.add(Value.of((Double) value));
            } else if (value != null) {
                arrayValue.add(Value.of(value.toString()));
            }
        }

        return arrayValue;
    }

    public int size() {
        return values.size();
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public List<Value> getValues() {
        return values;
    }

    public ArrayValue add(Value value) {
        values.add(value);
        return this;
    }

    public ArrayValue addAll(Collection<Value> values) {
        this.values.addAll(values);
        return this;
    }
}
