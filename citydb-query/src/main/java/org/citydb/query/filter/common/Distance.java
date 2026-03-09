/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.query.filter.common;

import org.citydb.database.srs.SrsUnit;

import java.util.Optional;

public class Distance {
    private final double value;
    private SrsUnit unit;

    private Distance(double value, SrsUnit unit) {
        this.value = value;
        this.unit = unit;
    }

    public static Distance of(double value, SrsUnit unit) {
        return new Distance(value, unit);
    }

    public static Distance of(double value) {
        return new Distance(value, null);
    }

    public double getValue() {
        return value;
    }

    public Optional<SrsUnit> getUnit() {
        return Optional.ofNullable(unit);
    }

    public Distance setUnit(SrsUnit unit) {
        this.unit = unit;
        return this;
    }
}
