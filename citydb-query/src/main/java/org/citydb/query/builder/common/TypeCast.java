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
