/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.database.srs;

import si.uom.NonSI;
import si.uom.SI;
import systems.uom.common.USCustomary;

import javax.measure.MetricPrefix;
import javax.measure.Unit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum SrsUnit {
    CENTIMETER(MetricPrefix.CENTI(SI.METRE), "cm", "centimetre", "centimeter", "centimetres", "centimeters"),
    DEGREE_ANGLE(NonSI.DEGREE_ANGLE, "deg", "degree", "degree angle", "degrees", "degrees angle"),
    FOOT(USCustomary.FOOT, "ft", "foot", "feet"),
    INCH(USCustomary.INCH, "in", "inch", "inches"),
    KILOMETER(MetricPrefix.KILO(SI.METRE), "km", "kilometre", "kilometer", "kilometres", "kilometers"),
    METER(SI.METRE, "m", "metre", "meter", "metres", "meters"),
    MILE(USCustomary.MILE, "mi", "mile", "miles"),
    MILLIMETER(MetricPrefix.MILLI(SI.METRE), "mm", "millimetre", "millimeter", "millimetres", "millimeters"),
    NAUTICAL_MILE(USCustomary.NAUTICAL_MILE, "nmi", "nautical mile", "nautical miles"),
    RADIAN(SI.RADIAN, "rad", "radian", "radians"),
    YARD(USCustomary.YARD, "yd", "yard", "yards");

    private final static Map<String, SrsUnit> units = new HashMap<>();
    private final Unit<?> unit;
    private final String[] symbols;

    static {
        Arrays.stream(values()).forEach(unit ->
                Arrays.stream(unit.symbols).forEach(symbol -> units.put(symbol.toLowerCase(Locale.ROOT), unit)));
    }

    SrsUnit(Unit<?> unit, String... symbols) {
        this.unit = unit;
        this.symbols = symbols;
    }

    public static SrsUnit of(String symbol) {
        return symbol != null ? units.get(symbol.toLowerCase(Locale.ROOT)) : null;
    }

    public Unit<?> getUnit() {
        return unit;
    }

    public String getSymbol() {
        return symbols[0];
    }

    @Override
    public String toString() {
        return symbols[0];
    }
}
