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

package org.citydb.database.geometry;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SrsIdentifierParser {
    private final Pattern httpPattern = Pattern.compile(
            "https?://www.opengis.net/def/crs/([^/]+?)/[^/]+?/([^/]+?)(?:/.*)?", Pattern.CASE_INSENSITIVE);
    private final Pattern urnPattern = Pattern.compile(
            "urn:ogc:def:crs(?:,crs)?:([^:]+?):[^:]*?:([^,]+?)(?:,.*)?", Pattern.CASE_INSENSITIVE);
    private final Pattern epsgPattern = Pattern.compile("EPSG:([0-9]+)", Pattern.CASE_INSENSITIVE);
    private final Matcher matcher = Pattern.compile("").matcher("");

    private SrsIdentifierParser() {
    }

    public static SrsIdentifierParser newInstance() {
        return new SrsIdentifierParser();
    }

    public int parse(String identifier) throws SrsParseException {
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
            throw new SrsParseException("Unknown SRS identifier schema '" + identifier + "'.");
        }
    }

    private int parseHttpSchema(String identifier) throws SrsParseException {
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

    private int parseUrnSchema(String identifier) throws SrsParseException {
        matcher.reset(identifier).usePattern(urnPattern);
        return matcher.matches() ?
                parseCode(matcher.group(2), identifier) :
                0;
    }

    private int parseEpsgSchema(String identifier) throws SrsParseException {
        matcher.reset(identifier).usePattern(epsgPattern);
        return matcher.matches() ?
                parseCode(matcher.group(1), identifier) :
                0;
    }

    private int parseCode(String code, String identifier) throws SrsParseException {
        try {
            return Integer.parseInt(code);
        } catch (NumberFormatException e) {
            throw new SrsParseException("Failed to parse '" + code + "' as SRS code from '" + identifier + "'", e);
        }
    }
}
