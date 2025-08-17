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

package org.citydb.cli.exporter.options;

import org.citydb.cli.common.Option;
import org.citydb.database.srs.SrsUnit;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Envelope;
import org.citydb.util.tiling.Tiling;
import org.citydb.util.tiling.options.Dimension;
import org.citydb.util.tiling.options.DimensionScheme;
import org.citydb.util.tiling.options.MatrixScheme;
import org.citydb.util.tiling.options.TileMatrixOrigin;
import picocli.CommandLine;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TilingOptions implements Option {
    public enum Origin {top_left, bottom_left}

    @CommandLine.Option(names = "--tile-matrix", paramLabel = "<columns,rows>",
            description = "Export tiles in a columns x rows grid.")
    private String matrixScheme;

    @CommandLine.Option(names = "--tile-dimension", paramLabel = "<width[unit],height[unit]>",
            description = "Export tiles with specified width and height, aligned with the database CRS grid " +
                    "(default length unit of the CRS assumed).")
    private String dimensionScheme;

    @CommandLine.Option(names = "--tile-extent", paramLabel = "<x_min,y_min,x_max,y_max[,srid]>",
            description = "Extent to use for tiling (default: auto-computed).")
    private String extent;

    @CommandLine.Option(names = "--tile-origin", defaultValue = "top_left",
            description = "Tile indexes origin: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private Origin origin;

    private Tiling tiling;

    public Tiling getTiling() {
        return tiling;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (matrixScheme == null && dimensionScheme == null) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: Either --tile-matrix or --tile-dimension is required as tiling scheme");
        } else if (matrixScheme != null && dimensionScheme != null) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --tile-matrix and --tile-dimension are mutually exclusive (specify only one)");
        }

        tiling = new Tiling();
        if (matrixScheme != null) {
            tiling.setScheme(getMatrixScheme(commandLine));
        } else if (dimensionScheme != null) {
            tiling.setScheme(getDimensionScheme(commandLine));
        }

        if (extent != null) {
            tiling.setExtent(getExtent(commandLine));
        }

        if (origin != null) {
            tiling.setTileMatrixOrigin(switch (origin) {
                case top_left -> TileMatrixOrigin.TOP_LEFT;
                case bottom_left -> TileMatrixOrigin.BOTTOM_LEFT;
            });
        }
    }

    private MatrixScheme getMatrixScheme(CommandLine commandLine) {
        String[] parts = matrixScheme.split(",");
        if (parts.length == 2) {
            return MatrixScheme.of(parseMatrixValue(parts[0], commandLine),
                    parseMatrixValue(parts[1], commandLine));
        } else {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: The matrix tiling scheme must be in COLUMNS,ROWS format but was '" + matrixScheme + "'");
        }
    }

    private DimensionScheme getDimensionScheme(CommandLine commandLine) {
        String[] parts = dimensionScheme.split(",");
        if (parts.length == 2) {
            Pattern pattern = Pattern.compile("(-?\\d*(?:\\.\\d+)?)([a-zA-Z]+)?");
            return DimensionScheme.of(parseDimension(parts[0], pattern, commandLine),
                    parseDimension(parts[1], pattern, commandLine));
        } else {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: The dimension tiling scheme must be in WIDTH[UNIT],HEIGHT[UNIT] format " +
                            "but was '" + dimensionScheme + "'");
        }
    }

    private Envelope getExtent(CommandLine commandLine) {
        String[] parts = extent.split(",");
        if (parts.length == 4 || parts.length == 5) {
            Envelope envelope;
            try {
                envelope = Envelope.of(Coordinate.of(Double.parseDouble(parts[0]), Double.parseDouble(parts[1])),
                        Coordinate.of(Double.parseDouble(parts[2]), Double.parseDouble(parts[3])));
            } catch (NumberFormatException e) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: The coordinates of the tiling extent must be floating point numbers but were '" +
                                String.join(",", parts[0], parts[1], parts[2], parts[3]) + "'");
            }

            if (parts.length == 5) {
                try {
                    envelope.setSRID(Integer.parseInt(parts[4]));
                } catch (NumberFormatException e) {
                    throw new CommandLine.ParameterException(commandLine,
                            "Error: The SRID of the tiling extent must be an integer but was '" + parts[4] + "'");
                }
            }

            return envelope;
        } else {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: The tiling extent must be in X_MIN,Y_MIN,X_MAX,Y_MAX[,SRID] format " +
                            "but was '" + extent + "'");
        }
    }

    private Dimension parseDimension(String input, Pattern pattern, CommandLine commandLine) {
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return Dimension.of(parseLength(matcher.group(1), commandLine),
                    parseUnit(matcher.group(2), commandLine));
        } else {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: A dimension used in the dimension tiling scheme must be in LENGTH[UNIT] format " +
                            "but was '" + input + "'");
        }
    }

    private int parseMatrixValue(String input, CommandLine commandLine) {
        try {
            int value = Integer.parseInt(input);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException e) {
            //
        }

        throw new CommandLine.ParameterException(commandLine,
                "Error: The columns and rows values of the matrix tiling scheme must be positive integers " +
                        "but were '" + matrixScheme + "'");
    }

    private double parseLength(String input, CommandLine commandLine) {
        try {
            double value = Double.parseDouble(input);
            if (value > 0) {
                return value;
            }
        } catch (NumberFormatException e) {
            //
        }

        throw new CommandLine.ParameterException(commandLine,
                "Error: A length used in the dimension tiling scheme must be a positive number " +
                        "but was '" + input + "'");
    }

    private SrsUnit parseUnit(String input, CommandLine commandLine) {
        if (input != null) {
            SrsUnit unit = SrsUnit.of(input);
            if (unit != null) {
                return unit;
            }
        } else {
            return null;
        }

        throw new CommandLine.ParameterException(commandLine,
                "Error: Unsupported length unit '" + input + "'. Use one of '" +
                        String.join("', '", Arrays.stream(SrsUnit.values()).map(SrsUnit::toString).toList()) + "'.");
    }
}
