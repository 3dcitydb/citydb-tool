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

package org.citydb.database.schema;

import java.util.*;
import java.util.stream.Collectors;

public enum Table {
    ADDRESS("address", Collections.emptySet()),
    ADE("ade", Collections.emptySet()),
    CODELIST("codelist", Collections.emptySet()),
    DATABASE_SRS("database_srs", Collections.emptySet()),
    TEX_IMAGE("tex_image", Collections.emptySet()),
    CODELIST_ENTRY("codelist_entry", Set.of(CODELIST)),
    NAMESPACE("namespace", Set.of(ADE)),
    DATATYPE("datatype", Set.of(ADE, NAMESPACE)),
    OBJECTCLASS("objectclass", Set.of(ADE, NAMESPACE)),
    FEATURE("feature", Set.of(OBJECTCLASS)),
    GEOMETRY_DATA("geometry_data", Set.of(FEATURE)),
    IMPLICIT_GEOMETRY("implicit_geometry", Set.of(GEOMETRY_DATA)),
    APPEARANCE("appearance", Set.of(FEATURE, IMPLICIT_GEOMETRY)),
    PROPERTY("property", Set.of(ADDRESS, APPEARANCE, DATATYPE, FEATURE, GEOMETRY_DATA, IMPLICIT_GEOMETRY, NAMESPACE)),
    SURFACE_DATA("surface_data", Set.of(OBJECTCLASS, TEX_IMAGE)),
    APPEAR_TO_SURFACE_DATA("appear_to_surface_data", Set.of(APPEARANCE, SURFACE_DATA)),
    SURFACE_DATA_MAPPING("surface_data_mapping", Set.of(GEOMETRY_DATA, SURFACE_DATA));

    private final static Map<String, Table> types = new HashMap<>();
    private final String name;
    private final Set<Table> dependencies;

    static {
        Arrays.stream(values()).forEach(type -> types.put(type.name, type));
    }

    Table(String name, Set<Table> dependencies) {
        this.name = name;
        this.dependencies = dependencies;
    }

    public static Table of(String name) {
        return name != null ? types.get(name.toLowerCase(Locale.ROOT)) : null;
    }

    public String getName() {
        return name;
    }

    public Set<Table> getDependencies() {
        return getDependencies(true);
    }

    public Set<Table> getDependencies(boolean transitive) {
        return transitive ?
                Arrays.stream(values())
                        .filter(this::dependsOn)
                        .collect(Collectors.toSet()) :
                dependencies;
    }

    public boolean dependsOn(Table other) {
        for (Table dependency : dependencies) {
            if (dependency == other || dependency.dependsOn(other)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String toString() {
        return name;
    }
}
