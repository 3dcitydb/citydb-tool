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

package org.citydb.database.schema;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum GeometryType implements ColumnType {
    ABSTRACT_GEOMETRY("core:AbstractGeometry"),
    ABSTRACT_SURFACE("core:AbstractSurface"),
    ABSTRACT_SOLID("core:AbstractSolid"),
    ENVELOPE("core:Envelope"),
    POINT("core:Point"),
    MULTI_POINT("core:MultiPoint"),
    LINE_STRING("core:LineString"),
    MULTI_LINE_STRING("core:MultiLineString"),
    POLYGON("core:Polygon"),
    COMPOSITE_SURFACE("core:CompositeSurface"),
    TRIANGULATED_SURFACE("core:TriangulatedSurface"),
    MULTI_SURFACE("core:MultiSurface"),
    SOLID("core:Solid"),
    COMPOSITE_SOLID("core:CompositeSolid"),
    MULTI_SOLID("core:MultiSolid");

    private final static Map<String, GeometryType> types = new HashMap<>();
    private final String name;

    static {
        Arrays.stream(values()).forEach(type -> types.put(type.name.toLowerCase(Locale.ROOT), type));
    }

    GeometryType(String name) {
        this.name = name;
    }

    public static GeometryType of(String name) {
        return name != null ? types.get(name.toLowerCase(Locale.ROOT)) : null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
