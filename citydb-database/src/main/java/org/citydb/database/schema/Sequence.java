/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.schema;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum Sequence {
    ADE("ade_seq"),
    ADDRESS("address_seq"),
    APPEARANCE("appearance_seq"),
    CODELIST_ENTRY("codelist_entry_seq"),
    CODELIST("codelist_seq"),
    FEATURE("feature_seq"),
    FEATURE_CHANGELOG("feature_changelog_seq"),
    GEOMETRY_DATA("geometry_data_seq"),
    IMPLICIT_GEOMETRY("implicit_geometry_seq"),
    PROPERTY("property_seq"),
    SURFACE_DATA("surface_data_seq"),
    APPEAR_TO_SURFACE_DATA("appear_to_surface_data_seq"),
    TEX_IMAGE("tex_image_seq");

    private final static Map<String, Sequence> sequences = new HashMap<>();
    private final String name;

    static {
        Arrays.stream(values()).forEach(sequence -> sequences.put(sequence.name.toLowerCase(Locale.ROOT), sequence));
    }

    Sequence(String name) {
        this.name = name;
    }

    public static Sequence of(String name) {
        return name != null ? sequences.get(name.toLowerCase(Locale.ROOT)) : null;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
