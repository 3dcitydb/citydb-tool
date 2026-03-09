/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
                case STRING -> Type.STRING;
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
