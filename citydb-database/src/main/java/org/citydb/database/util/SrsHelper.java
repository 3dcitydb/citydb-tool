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

package org.citydb.database.util;

import org.citydb.database.adapter.DatabaseAdapter;
import org.citydb.database.srs.SrsException;
import org.citydb.database.srs.SrsUnit;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.measure.Units;

import javax.measure.Unit;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SrsHelper {
    private final DatabaseAdapter adapter;
    private final Pattern httpPattern = Pattern.compile(
            "https?://www.opengis.net/def/crs/([^/]+?)/[^/]+?/([^/]+?)(?:/.*)?", Pattern.CASE_INSENSITIVE);
    private final Pattern urnPattern = Pattern.compile(
            "urn:ogc:def:crs(?:,crs)?:([^:]+?):[^:]*?:([^,]+?)(?:,.*)?", Pattern.CASE_INSENSITIVE);
    private final Pattern epsgPattern = Pattern.compile("EPSG:([0-9]+)", Pattern.CASE_INSENSITIVE);
    private final Matcher matcher = Pattern.compile("").matcher("");

    private SrsHelper(DatabaseAdapter adapter) {
        this.adapter = adapter;
    }

    public static SrsHelper newInstance(DatabaseAdapter adapter) {
        return new SrsHelper(adapter);
    }

    public String getDefaultIdentifier(int srid) {
        return "http://www.opengis.net/def/crs/EPSG/0/" + srid;
    }

    public int parseSRID(String identifier) throws SrsException {
        int code = 0;
        if (identifier != null && !identifier.isEmpty()) {
            String candidate = identifier.toLowerCase(Locale.ROOT);
            if (candidate.startsWith("http")) {
                code = parseHttpSchema(identifier);
            } else if (candidate.startsWith("urn")) {
                code = parseUrnSchema(identifier);
            } else if (candidate.startsWith("epsg")) {
                code = parseEpsgSchema(identifier);
            }
        }

        if (code != 0) {
            return code;
        } else {
            throw new SrsException("Unknown SRS identifier schema '" + identifier + "'.");
        }
    }

    private int parseHttpSchema(String identifier) throws SrsException {
        matcher.reset(identifier).usePattern(httpPattern);
        if (matcher.matches()) {
            String code = matcher.group(2);
            if (code.equalsIgnoreCase("CRS84")) {
                return 4326;
            } else if (code.equalsIgnoreCase("CRS84h")) {
                return 4979;
            } else {
                return parseCode(code, identifier);
            }
        }

        return 0;
    }

    private int parseUrnSchema(String identifier) throws SrsException {
        matcher.reset(identifier).usePattern(urnPattern);
        return matcher.matches() ?
                parseCode(matcher.group(2), identifier) :
                0;
    }

    private int parseEpsgSchema(String identifier) throws SrsException {
        matcher.reset(identifier).usePattern(epsgPattern);
        return matcher.matches() ?
                parseCode(matcher.group(1), identifier) :
                0;
    }

    private int parseCode(String code, String identifier) throws SrsException {
        try {
            return Integer.parseInt(code);
        } catch (NumberFormatException e) {
            throw new SrsException("Failed to parse '" + code + "' as SRS code from '" + identifier + "'", e);
        }
    }

    public double convert(double value, SrsUnit fromUnit) throws SrsException {
        if (fromUnit != null) {
            CoordinateReferenceSystem crs = adapter.getDatabaseMetadata()
                    .getSpatialReference()
                    .getDefinition().orElse(null);
            if (crs != null && crs.getCoordinateSystem().getDimension() > 0) {
                try {
                    Unit<?> toUnit = crs.getCoordinateSystem().getAxis(0).getUnit();
                    return Units.getConverterToAny(fromUnit.getUnit(), toUnit).convert(value);
                } catch (Exception e) {
                    throw new SrsException("Failed to convert from the unit '" + fromUnit.getSymbol() + "' " +
                            "to the unit of the database SRS.", e);
                }
            } else {
                throw new SrsException("Failed to retrieve the unit of the database SRS.");
            }
        } else {
            return value;
        }
    }
}
