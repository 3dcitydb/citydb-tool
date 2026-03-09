/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.adapter.geometry.builder;

public class Lod {
    public static final Lod NONE = new Lod(null);
    private final String value;

    private Lod(String value) {
        this.value = value;
    }

    public static Lod of(String value) {
        return new Lod(value);
    }

    public static Lod of(int value) {
        return new Lod(String.valueOf(value));
    }

    public String getValue() {
        return value;
    }
}
