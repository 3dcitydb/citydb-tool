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

package org.citydb.operation.importer.reference;

import org.citydb.database.schema.Table;

public enum CacheType {
    FEATURE(Table.PROPERTY, "val_feature_id"),
    IMPLICIT_GEOMETRY(Table.PROPERTY, "val_implicitgeom_id"),
    ADDRESS(Table.PROPERTY, "val_address_id"),
    SURFACE_DATA(Table.APPEAR_TO_SURFACE_DATA, "surface_data_id"),
    TEXTURE_IMAGE(Table.SURFACE_DATA, "tex_image_id");

    private final Table table;
    private final String column;

    CacheType(Table table, String column) {
        this.table = table;
        this.column = column;
    }

    public Table getTable() {
        return table;
    }

    public String getColumn() {
        return column;
    }
}
