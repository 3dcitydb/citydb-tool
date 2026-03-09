/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.builder.common;

import org.citydb.model.property.DataType;

import java.util.*;

public enum TypeCast {
    BOOLEAN(List.of("bool", "boolean"), DataType.BOOLEAN),
    INTEGER(List.of("int", "integer"), DataType.INTEGER),
    DOUBLE(List.of("double"), DataType.DOUBLE),
    STRING(List.of("string"), DataType.STRING),
    TIMESTAMP(List.of("timestamp", "dateTime"), DataType.TIMESTAMP),
    URI(List.of("uri"), DataType.URI),
    CODE(List.of("code"), DataType.CODE),
    MEASURE(List.of("measure"), DataType.MEASURE),
    GEOMETRY(List.of("geometry", "geometryProperty"), DataType.GEOMETRY_PROPERTY),
    IMPLICIT_GEOMETRY(List.of("implicitGeometry", "implicitGeometryProperty"), DataType.IMPLICIT_GEOMETRY_PROPERTY),
    FEATURE_PROPERTY(List.of("feature", "featureProperty"), DataType.FEATURE_PROPERTY);

    private final static Map<String, TypeCast> types = new HashMap<>();
    private final List<String> identifiers;
    private final DataType type;

    static {
        Arrays.stream(values()).forEach(type ->
                type.identifiers.forEach(identifier -> types.put(identifier.toLowerCase(Locale.ROOT), type)));
    }

    TypeCast(List<String> identifiers, DataType type) {
        this.identifiers = identifiers;
        this.type = type;
    }

    public static TypeCast of(String identifier) {
        return identifier != null ? types.get(identifier.toLowerCase(Locale.ROOT)) : null;
    }

    public String getIdentifier() {
        return identifiers.get(0);
    }

    public DataType getType() {
        return type;
    }

    @Override
    public String toString() {
        return identifiers.get(0);
    }
}
