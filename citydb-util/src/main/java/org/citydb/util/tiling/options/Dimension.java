/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.util.tiling.options;

import org.citydb.database.srs.SrsUnit;

import java.util.Optional;

public class Dimension {
    private double value;
    private SrsUnit unit;

    public static Dimension of(double value, SrsUnit unit) {
        return new Dimension().setValue(value).setUnit(unit);
    }

    public static Dimension of(double value) {
        return of(value, null);
    }

    public double getValue() {
        return value;
    }

    public Dimension setValue(double value) {
        this.value = value;
        return this;
    }

    public Optional<SrsUnit> getUnit() {
        return Optional.ofNullable(unit);
    }

    public Dimension setUnit(SrsUnit unit) {
        this.unit = unit;
        return this;
    }
}
