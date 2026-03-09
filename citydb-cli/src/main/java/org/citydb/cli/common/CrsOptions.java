/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.common;

import org.citydb.config.common.SrsReference;
import picocli.CommandLine;

public class CrsOptions implements Option {
    @CommandLine.Option(names = {"--crs"},
            description = "SRID or identifier of the CRS to use for the coordinates of geometries " +
                    "(default: storage CRS).")
    private String crs;

    @CommandLine.Option(names = {"--crs-name"},
            description = "Name of the CRS to use in the output file.")
    private String name;

    private SrsReference targetSrs;

    public SrsReference getTargetSrs() {
        return targetSrs;
    }

    public String getName() {
        return name;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (crs != null) {
            targetSrs = new SrsReference();
            try {
                targetSrs.setSRID(Integer.parseInt(crs));
            } catch (NumberFormatException e) {
                targetSrs.setIdentifier(crs);
            }
        }
    }
}
