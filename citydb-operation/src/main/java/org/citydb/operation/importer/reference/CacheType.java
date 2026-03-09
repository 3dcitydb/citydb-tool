/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
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
