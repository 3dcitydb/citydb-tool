/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.schema;

import org.citydb.model.common.Name;
import org.citydb.model.common.Namespaces;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum GeometryType implements ColumnType {
    ABSTRACT_GEOMETRY("core:AbstractGeometry", Name.of("AbstractGeometry", Namespaces.CORE)),
    ABSTRACT_SURFACE("core:AbstractSurface", Name.of("AbstractSurface", Namespaces.CORE)),
    ABSTRACT_SOLID("core:AbstractSolid", Name.of("AbstractSolid", Namespaces.CORE)),
    ENVELOPE("core:Envelope", Name.of("Envelope", Namespaces.CORE)),
    POINT("core:Point", Name.of("Point", Namespaces.CORE)),
    MULTI_POINT("core:MultiPoint", Name.of("MultiPoint", Namespaces.CORE)),
    LINE_STRING("core:LineString", Name.of("LineString", Namespaces.CORE)),
    MULTI_LINE_STRING("core:MultiLineString", Name.of("MultiLineString", Namespaces.CORE)),
    POLYGON("core:Polygon", Name.of("Polygon", Namespaces.CORE)),
    COMPOSITE_SURFACE("core:CompositeSurface", Name.of("CompositeSurface", Namespaces.CORE)),
    TRIANGULATED_SURFACE("core:TriangulatedSurface", Name.of("TriangulatedSurface", Namespaces.CORE)),
    MULTI_SURFACE("core:MultiSurface", Name.of("MultiSurface", Namespaces.CORE)),
    SOLID("core:Solid", Name.of("Solid", Namespaces.CORE)),
    COMPOSITE_SOLID("core:CompositeSolid", Name.of("CompositeSolid", Namespaces.CORE)),
    MULTI_SOLID("core:MultiSolid", Name.of("MultiSolid", Namespaces.CORE));

    private final static Map<String, GeometryType> types = new HashMap<>();
    private final String identifier;
    private final Name name;

    static {
        Arrays.stream(values()).forEach(type -> types.put(type.identifier.toLowerCase(Locale.ROOT), type));
    }

    GeometryType(String identifier, Name name) {
        this.identifier = identifier;
        this.name = name;
    }

    public static GeometryType of(String identifier) {
        return identifier != null ? types.get(identifier.toLowerCase(Locale.ROOT)) : null;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    public Name getName() {
        return name;
    }

    @Override
    public String toString() {
        return identifier;
    }
}
