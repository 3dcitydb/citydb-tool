/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences <https://www.hft-stuttgart.de>
 */

package org.citydb.cli.visExporter.tiles3d;

import org.citydb.cli.common.Option;
import picocli.CommandLine;

public class OutlineOptions implements Option {
    @CommandLine.Option(names = "--enable-outline",
            description = "Emit polygon boundary edges as outlines (glTF " +
                    "CESIUM_primitive_outline): CesiumJS draws the original polygon " +
                    "edges of each surface as lines on top of the geometry. The " +
                    "extension is Cesium-specific — other clients ignore it and render " +
                    "the geometry unchanged. Adds one edge-index buffer per primitive " +
                    "and increases viewer load time; omitted by default.")
    private boolean enableOutline;

    @CommandLine.Option(names = "--outline-merge-coplanar", arity = "0..1",
            fallbackValue = "0", paramLabel = "<degrees>",
            description = "Suppress outlines on edges shared by two coplanar surfaces " +
                    "of the same feature and surface type: source data that subdivides " +
                    "one plane into many polygons (triangle meshes, panelized walls) " +
                    "otherwise renders as a wireframe over the surface. Without a " +
                    "value, only exactly coplanar surfaces merge (CAD-authored " +
                    "subdivisions); pass a maximum dihedral angle in degrees to also " +
                    "merge surveyed facets whose planes carry angular noise (e.g. 5). " +
                    "Boundaries between different surface types (a door in its wall) " +
                    "and folds above the angle (roof ridges, facade kinks) keep their " +
                    "outline. Only takes effect together with --enable-outline.")
    private Double outlineMergeCoplanar;

    public boolean isEnableOutline() {
        return enableOutline;
    }

    public Double getOutlineMergeCoplanar() {
        return outlineMergeCoplanar;
    }

    @Override
    public void preprocess(CommandLine commandLine) {
        if (outlineMergeCoplanar != null
                && !(outlineMergeCoplanar >= 0 && outlineMergeCoplanar < 90)) {
            // The negated range check also rejects NaN, which every plain
            // comparison reports as false.
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --outline-merge-coplanar must be an angle between 0 (inclusive) " +
                            "and 90 degrees (exclusive) but was '" + outlineMergeCoplanar + "'");
        }
    }
}
