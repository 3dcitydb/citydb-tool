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

package org.citydb.query.builder.common;

import org.citydb.database.schema.*;

import java.util.EnumSet;

public enum Type {
    BOOLEAN("boolean"),
    INTEGER("integer"),
    DOUBLE("double"),
    STRING("string"),
    TIMESTAMP("timestamp"),
    DATE("date"),
    ARRAY("array"),
    ENVELOPE("envelope"),
    GEOMETRY("geometry"),
    IMPLICIT_GEOMETRY("implicitGeometry"),
    FEATURE("feature"),
    UNDEFINED("undefined");

    public static final EnumSet<Type> SCALAR_TYPES = EnumSet.of(BOOLEAN, INTEGER, DOUBLE, STRING, TIMESTAMP, DATE);
    public static final EnumSet<Type> NUMERIC_TYPES = EnumSet.of(INTEGER, DOUBLE);
    public static final EnumSet<Type> SPATIAL_TYPES = EnumSet.of(ENVELOPE, GEOMETRY);
    private final String identifier;

    Type(String identifier) {
        this.identifier = identifier;
    }

    public static Type of(ColumnType columnType) {
        if (columnType instanceof SimpleType simpleType) {
            return switch (simpleType) {
                case BOOLEAN -> Type.BOOLEAN;
                case INTEGER -> Type.INTEGER;
                case DOUBLE -> Type.DOUBLE;
                case STRING, URI -> Type.STRING;
                case TIMESTAMP -> Type.TIMESTAMP;
                case ARRAY, BOOLEAN_ARRAY, INTEGER_ARRAY, DOUBLE_ARRAY, STRING_ARRAY -> Type.ARRAY;
            };
        } else if (columnType instanceof GeometryType geometryType) {
            return geometryType == GeometryType.ENVELOPE ? ENVELOPE : GEOMETRY;
        } else {
            return Type.UNDEFINED;
        }
    }

    public String getIdentifier() {
        return identifier;
    }

    public DataType getSchemaType(SchemaMapping schemaMapping) {
        org.citydb.model.property.DataType dataType = switch (this) {
            case BOOLEAN -> org.citydb.model.property.DataType.BOOLEAN;
            case INTEGER -> org.citydb.model.property.DataType.INTEGER;
            case DOUBLE -> org.citydb.model.property.DataType.DOUBLE;
            case STRING -> org.citydb.model.property.DataType.STRING;
            case TIMESTAMP, DATE -> org.citydb.model.property.DataType.TIMESTAMP;
            case GEOMETRY -> org.citydb.model.property.DataType.GEOMETRY_PROPERTY;
            case IMPLICIT_GEOMETRY -> org.citydb.model.property.DataType.IMPLICIT_GEOMETRY_PROPERTY;
            case FEATURE -> org.citydb.model.property.DataType.FEATURE_PROPERTY;
            default -> null;
        };

        return dataType != null ?
                schemaMapping.getDataType(dataType.getName()) :
                DataType.UNDEFINED;
    }

    @Override
    public String toString() {
        return identifier;
    }
}
