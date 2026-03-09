/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.importer.options;

import org.citydb.cli.common.Option;
import org.citydb.io.reader.options.BboxMode;
import org.citydb.model.geometry.Coordinate;
import org.citydb.model.geometry.Envelope;
import picocli.CommandLine;

public class BboxOptions implements Option {
    private enum Mode {intersects, contains, on_tile}

    @CommandLine.Option(names = {"-b", "--bbox"}, paramLabel = "<x_min,y_min,x_max,y_max[,srid]>", required = true,
            description = "Bounding box to use as spatial filter.")
    private String bbox;

    @CommandLine.Option(names = "--bbox-mode", paramLabel = "<mode>", defaultValue = "intersects",
            description = "Bounding box mode: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private Mode mode;

    private Envelope envelope;

    public Envelope getEnvelope() {
        return envelope;
    }

    public BboxMode getMode() {
        return switch (mode) {
            case intersects -> BboxMode.INTERSECTS;
            case contains -> BboxMode.CONTAINS;
            case on_tile -> BboxMode.ON_TILE;
        };
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        String[] parts = bbox.split(",");
        if (parts.length == 4 || parts.length == 5) {
            try {
                envelope = Envelope.of(Coordinate.of(Double.parseDouble(parts[0]), Double.parseDouble(parts[1])),
                        Coordinate.of(Double.parseDouble(parts[2]), Double.parseDouble(parts[3])));
            } catch (NumberFormatException e) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: The coordinates of a bounding box must be floating point numbers but were '" +
                                String.join(",", parts[0], parts[1], parts[2], parts[3]) + "'");
            }

            if (parts.length == 5) {
                try {
                    envelope.setSRID(Integer.parseInt(parts[4]));
                } catch (NumberFormatException e) {
                    throw new CommandLine.ParameterException(commandLine,
                            "Error: The SRID of a bounding box must be an integer but was '" + parts[4] + "'");
                }
            }
        } else {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: A bounding box must be in X_MIN,Y_MIN,X_MAX,Y_MAX[,SRID] format but was '" + bbox + "'");
        }
    }
}
