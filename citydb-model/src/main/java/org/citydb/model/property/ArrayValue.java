/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
            if (value instanceof Boolean bool) {
                arrayValue.add(Value.of(bool));
            } else if (value instanceof Integer intValue) {
                arrayValue.add(Value.of(intValue));
            } else if (value instanceof Long longValue) {
                arrayValue.add(Value.of(longValue));
            } else if (value instanceof Double doubleValue) {
                arrayValue.add(Value.of(doubleValue));
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
