/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.cli.importer.util;

import org.citydb.cli.importer.ImportOptions;
import org.citydb.core.concurrent.LazyInitializer;
import org.citydb.core.file.InputFile;

import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImportOptionsHelper {
    private final String lineage;
    private final LazyInitializer<Pattern> lineagePattern = LazyInitializer.of(() ->
            Pattern.compile("@(file_path|file_name|content_path|content_name)@", Pattern.CASE_INSENSITIVE));

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
        StringBuilder result = new StringBuilder();
        Matcher matcher = lineagePattern.get().matcher(lineage);
        while (matcher.find()) {
            String token = matcher.group(1);
            String replacement = switch (token.toLowerCase(Locale.ROOT)) {
                case "file_path" -> inputFile.getFile().toString();
                case "file_name" -> inputFile.getFile().getFileName().toString();
                case "content_path" -> inputFile.getContentFile();
                case "content_name" -> Path.of(inputFile.getContentFile()).getFileName().toString();
                default -> token;
            };

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);
        return result.toString();
    }
}
