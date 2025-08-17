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

package org.citydb.database.postgres;

import org.citydb.database.geometry.GeometryException;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Envelope;

public class BoxParser {

    public Envelope parse(String text) throws GeometryException {
        return text != null ? readBox(text) : null;
    }

    private Envelope readBox(String text) throws GeometryException {
        String[] parts = split(text);
        Integer srid = parts[0] != null ? getSRID(parts[0]) : null;

        Envelope envelope = switch (parts[1]) {
            case "BOX" -> readBox(parts[2], 2);
            case "BOX3D" -> readBox(parts[2], 3);
            default -> throw new GeometryException("Unsupported box geometry type '" + parts[1] + "'.");
        };

        return envelope.setSRID(srid);
    }

    private Envelope readBox(String text, int dimension) throws GeometryException {
        String[] parts = text.split(",");
        if (parts.length == 2) {
            Coordinate lowerCorner = getCoordinate(parts[0], dimension);
            Coordinate upperCorner = getCoordinate(parts[1], dimension);
            return Envelope.of(lowerCorner, upperCorner);
        } else {
            throw new GeometryException("Failed to parse lower and upper corner of box geometry.");
        }
    }

    private Coordinate getCoordinate(String text, int dimension) throws GeometryException {
        String[] parts = text.trim().split(" ");
        if (parts.length == dimension) {
            double x = getNumber(parts[0]);
            double y = getNumber(parts[1]);
            return parts.length == 2 ?
                    Coordinate.of(x, y) :
                    Coordinate.of(x, y, getNumber(parts[2]));
        } else {
            throw new GeometryException("Invalid dimension of box coordinate.");
        }
    }

    private Double getNumber(String text) throws GeometryException {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new GeometryException("Invalid number: " + text);
        }
    }

    private Integer getSRID(String text) throws GeometryException {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new GeometryException("Invalid SRID: " + text);
        }
    }

    private String[] split(String text) {
        int srid = text.indexOf("SRID=");
        int semicolon = text.indexOf(";", srid);
        int lParen = text.indexOf("(", semicolon);
        int rParen = text.indexOf(")", lParen);

        return new String[]{srid != -1 ? text.substring(srid + 5, semicolon).trim() : null,
                text.substring(semicolon != -1 ? semicolon + 1 : 0, lParen != -1 ? lParen : 0).trim(),
                text.substring(lParen != -1 ? lParen + 1 : 0, rParen != -1 ? rParen : text.length())};
    }
}
