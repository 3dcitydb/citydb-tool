/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.cli.visExporter.options;

import org.citydb.cli.common.Option;
import picocli.CommandLine;

public class TilingOptions implements Option {
    @CommandLine.Option(names = "--grid-edge-length", paramLabel = "<meters>",
            description = "Edge length in meters of one grid cell used as the leaf of the " +
                    "spatial aggregation tree. When omitted, the cell edge is sized to the " +
                    "longest side of the dataset extent so the entire dataset fits in a " +
                    "single root cell (no spatial subdivision). Set an explicit smaller " +
                    "value to produce a finer grid and shorter camera load distances.")
    private double gridEdgeLength;

    @CommandLine.Option(names = "--screen-pixel-threshold", paramLabel = "<pixels>",
            defaultValue = "56",
            description = "Projected MBS radius (pixels) above which a tile refines to its " +
                    "children (default: ${DEFAULT-VALUE}). Applied uniformly to both 3D Tiles " +
                    "(via geometric error) and I3S (via LOD threshold) so both formats load " +
                    "the same level of detail at any given camera distance. Lower values load " +
                    "more detail (heavier viewer), higher values defer refinement (lighter " +
                    "viewer). Pass 0 to always refine to the leaves; this can crash the viewer " +
                    "on city-scale datasets and is intended for small exports or debugging.")
    private double screenPixelThreshold;

    public double getGridEdgeLength() {
        return gridEdgeLength;
    }

    public double getScreenPixelThreshold() {
        return screenPixelThreshold;
    }

    @Override
    public void preprocess(CommandLine commandLine) {
        if (gridEdgeLength < 0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --grid-edge-length must be a non-negative number of meters but was '" +
                            gridEdgeLength + "'");
        }

        if (screenPixelThreshold < 0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --screen-pixel-threshold must be a non-negative number of pixels but was '" +
                            screenPixelThreshold + "'");
        }
    }
}
