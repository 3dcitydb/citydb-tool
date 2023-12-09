/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

package org.citydb.model.property;

import java.io.Serializable;
import java.util.Objects;

public class Value implements Serializable {
    private final Object value;

    private Value(Object value) {
        this.value = Objects.requireNonNull(value, "The value must not be null.");
    }

    public static Value of(boolean value) {
        return new Value(value);
    }

    public static Value of(int value) {
        return new Value(value);
    }

    public static Value of(long value) {
        return new Value(value);
    }

    public static Value of(double value) {
        return new Value(value);
    }

    public static Value of(String value) {
        return new Value(value);
    }

    public Object rawValue() {
        return value;
    }

    public boolean isNumber() {
        return value instanceof Number;
    }

    public boolean isBoolean() {
        return value instanceof Boolean;
    }

    public boolean booleanValue() {
        return value instanceof Boolean booleanValue ? booleanValue : false;
    }

    public boolean asBoolean() {
        return asBoolean(false);
    }

    public boolean asBoolean(boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        } else if (value instanceof Number number) {
            return number.doubleValue() != 0;
        } else if (value instanceof String) {
            if ("true".equalsIgnoreCase(value.toString())
                    || "1".equals(value)) {
                return true;
            } else if ("false".equalsIgnoreCase(value.toString())
                    || "0".equals(value)) {
                return false;
            }
        }

        return defaultValue;
    }

    public boolean isInt() {
        return value instanceof Integer;
    }

    public boolean canCastToInt() {
        if (value instanceof Number number) {
            double tmp = number.doubleValue();
            return tmp >= Integer.MIN_VALUE && tmp <= Integer.MAX_VALUE;
        }

        return false;
    }

    public int intValue() {
        return value instanceof Number number ? number.intValue() : 0;
    }

    public int asInt() {
        return asInt(0);
    }

    public int asInt(int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        } else if (value instanceof Boolean bool) {
            return bool ? 1 : 0;
        } else if (value instanceof String str) {
            try {
                return Integer.parseInt(str);
            } catch (Exception e) {
                //
            }
        }

        return defaultValue;
    }

    public boolean isLong() {
        return value instanceof Long;
    }

    public boolean canCastToLong() {
        if (value instanceof Number number) {
            double doubleValue = number.doubleValue();
            return doubleValue >= Long.MIN_VALUE && doubleValue <= Long.MAX_VALUE;
        }

        return false;
    }

    public long longValue() {
        return value instanceof Number number ? number.longValue() : 0;
    }

    public long asLong() {
        return asLong(0);
    }

    public long asLong(long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        } else if (value instanceof Boolean bool) {
            return bool ? 1 : 0;
        } else if (value instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (Exception e) {
                //
            }
        }

        return defaultValue;
    }

    public boolean isDouble() {
        return value instanceof Number;
    }

    public boolean canCastToDouble() {
        return value instanceof Number;
    }

    public double doubleValue() {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    public double asDouble() {
        return asDouble(0);
    }

    public double asDouble(double defaultValue) {
        if (value instanceof Number number) {
            return number.doubleValue();
        } else if (value instanceof Boolean bool) {
            return bool ? 1 : 0;
        } else if (value instanceof String str) {
            try {
                return Double.parseDouble(str);
            } catch (Exception e) {
                //
            }
        }

        return defaultValue;
    }

    public boolean isString() {
        return value instanceof String;
    }

    public String stringValue() {
        return value instanceof String str ? str : null;
    }

    public String asString() {
        return asString("");
    }

    public String asString(String defaultValue) {
        if (value instanceof String str) {
            return str;
        } else if (value instanceof Boolean bool) {
            return bool ? "true" : "false";
        } else if (value instanceof Number) {
            return String.valueOf(value);
        }

        return defaultValue;
    }
}
