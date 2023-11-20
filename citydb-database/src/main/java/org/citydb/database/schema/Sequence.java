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

package org.citydb.database.schema;

public enum Sequence {
    ADE("ade_seq"),
    ADDRESS("address_seq"),
    APPEARANCE("appearance_seq"),
    CODELIST_ENTRY("codelist_entry_seq"),
    CODELIST("codelist_seq"),
    FEATURE("feature_seq"),
    GEOMETRY_DATA("geometry_data_seq"),
    IMPLICIT_GEOMETRY("implicit_geometry_seq"),
    PROPERTY("property_seq"),
    SURFACE_DATA("surface_data_seq"),
    APPEAR_TO_SURFACE_DATA("appear_to_surface_data_seq"),
    TEX_IMAGE("tex_image_seq");

    private final String name;

    Sequence(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
