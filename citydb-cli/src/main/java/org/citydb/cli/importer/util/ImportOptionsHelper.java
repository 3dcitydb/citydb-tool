/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.importer.util;

import org.citydb.cli.importer.ImportOptions;
import org.citydb.cli.util.TokenReplacer;
import org.citydb.core.file.InputFile;

public class ImportOptionsHelper {
    private final String lineage;

    private ImportOptionsHelper(String lineage) {
        this.lineage = lineage;
    }

    public static ImportOptionsHelper of(ImportOptions options) {
        return new ImportOptionsHelper(options.getLineage().orElse(null));
    }

    public ImportOptions update(ImportOptions options, InputFile inputFile) {
        if (lineage != null) {
            options.setLineage(updateLineage(inputFile));
        }

        return options;
    }

    private String updateLineage(InputFile inputFile) {
        return TokenReplacer.replaceFileTokens(lineage, inputFile);
    }
}
