/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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

package org.citydb.operation.importer.reference;

import org.citydb.database.schema.Table;

public enum CacheType {
    FEATURE(Table.PROPERTY, "val_feature_id", "feature"),
    IMPLICIT_GEOMETRY(Table.PROPERTY, "val_implicitgeom_id", "implicit geometry"),
    ADDRESS(Table.PROPERTY, "val_address_id", "address"),
    SURFACE_DATA(Table.APPEAR_TO_SURFACE_DATA, "surface_data_id", "surface data"),
    TEXTURE_IMAGE(Table.SURFACE_DATA, "tex_image_id", "texture image");

    private final Table table;
    private final String column;
    private final String label;

    CacheType(Table table, String column, String label) {
        this.table = table;
        this.column = column;
        this.label = label;
    }

    public Table getTable() {
        return table;
    }

    public String getColumn() {
        return column;
    }

    public String getLabel() {
        return label;
    }
}
