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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Index {
    public static final Index FEATURE_OBJECTID = Index.normal("feature_objectid_inx", Table.FEATURE.getName(), "objectid");
    public static final Index FEATURE_IDENTIFIER = Index.normal("feature_identifier_inx", Table.FEATURE.getName(), List.of("identifier", "identifier_codespace"));
    public static final Index FEATURE_ENVELOPE = Index.spatial("feature_envelope_spx", Table.FEATURE.getName(), "envelope");
    public static final Index FEATURE_CREATION_DATE = Index.normal("feature_creation_date_inx", Table.FEATURE.getName(), "creation_date");
    public static final Index FEATURE_TERMINATION_DATE = Index.normal("feature_termination_date_inx", Table.FEATURE.getName(), "termination_date");
    public static final Index FEATURE_VALID_FROM = Index.normal("feature_valid_from_inx", Table.FEATURE.getName(), "valid_from");
    public static final Index FEATURE_VALID_TO = Index.normal("feature_valid_to_inx", Table.FEATURE.getName(), "valid_to");
    public static final Index GEOMETRY_DATA_GEOMETRY = Index.spatial("geometry_data_spx", Table.GEOMETRY_DATA.getName(), "geometry");
    public static final Index IMPLICIT_GEOMETRY_OBJECTID = Index.normal("implicit_geometry_objectid_inx", Table.IMPLICIT_GEOMETRY.getName(), "objectid");
    public static final Index PROPERTY_NAME = Index.normal("property_name_inx", Table.PROPERTY.getName(), "name");
    public static final Index PROPERTY_NAMESPACE = Index.normal("property_namespace_inx", Table.PROPERTY.getName(), "namespace_id");
    public static final Index PROPERTY_VAL_TIMESTAMP = Index.normal("property_val_date_inx", Table.PROPERTY.getName(), "val_timestamp");
    public static final Index PROPERTY_VAL_DOUBLE = Index.normal("property_val_double_inx", Table.PROPERTY.getName(), "val_double");
    public static final Index PROPERTY_VAL_INT = Index.normal("property_val_int_inx", Table.PROPERTY.getName(), "val_int");
    public static final Index PROPERTY_VAL_LOD = Index.normal("property_val_lod_inx", Table.PROPERTY.getName(), "val_lod");
    public static final Index PROPERTY_VAL_RELATION_TYPE = Index.normal("property_val_relation_type_inx", Table.PROPERTY.getName(), "val_relation_type");
    public static final Index PROPERTY_VAL_STRING = Index.normal("property_val_string_inx", Table.PROPERTY.getName(), "val_string");
    public static final Index PROPERTY_VAL_UOM = Index.normal("property_val_uom_inx", Table.PROPERTY.getName(), "val_uom");
    public static final Index PROPERTY_VAL_URI = Index.normal("property_val_uri_inx", Table.PROPERTY.getName(), "val_uri");

    private final String name;
    private final String table;
    private final List<String> columns;
    private final Type type;

    public enum Type {
        NORMAL,
        SPATIAL
    }

    private Index(String name, String table, List<String> columns, Type type) {
        this.name = Objects.requireNonNull(name, "The index name must not be null.");
        this.table = Objects.requireNonNull(table, "The index table must not be null.");
        this.type = Objects.requireNonNull(type, "The index type must not be null.");

        Objects.requireNonNull(columns, "The index columns must not be null.");
        if (!columns.isEmpty()) {
            this.columns = new ArrayList<>(columns);
        } else {
            throw new IllegalArgumentException("The index columns must not be empty.");
        }
    }

    public static Index of(String name, String table, List<String> columns, Type type) {
        return new Index(name, table, columns, type);
    }

    public static Index of(String name, String table, String column, Type type) {
        return of(name, table, List.of(column), type);
    }

    public static Index normal(String name, String table, List<String> columns) {
        return of(name, table, columns, Type.NORMAL);
    }

    public static Index normal(String name, String table, String column) {
        return normal(name, table, List.of(column));
    }

    public static Index spatial(String name, String table, List<String> columns) {
        return of(name, table, columns, Type.SPATIAL);
    }

    public static Index spatial(String name, String table, String column) {
        return spatial(name, table, List.of(column));
    }

    public String getName() {
        return name;
    }

    public String getTable() {
        return table;
    }

    public List<String> getColumns() {
        return columns;
    }

    public Type getType() {
        return type;
    }

    @Override
    public String toString() {
        return table + "(" + String.join(", ", columns) + ")";
    }
}
