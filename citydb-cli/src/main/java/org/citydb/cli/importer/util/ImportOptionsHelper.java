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
