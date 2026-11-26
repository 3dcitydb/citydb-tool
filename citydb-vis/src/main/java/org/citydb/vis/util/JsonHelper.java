/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Stuttgart University of Applied Sciences (HFT Stuttgart) <https://www.hft-stuttgart.de>
 */

package org.citydb.vis.util;

import org.citydb.core.json.JsonMapping;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared JSON serialization utility for visualization format encoders.
 * <p>
 * Builds on the project-wide {@link JsonMapping} mapper (field-based, nulls
 * omitted) with two adjustments for the written scene files:
 * <ul>
 *   <li>properties keep their declaration order instead of Jackson's default
 *       alphabetical order, so the model classes control the layout of the
 *       output;</li>
 *   <li>a platform-independent pretty printer: two-space indentation with
 *       {@code \n} line breaks, so the written bytes do not depend on the
 *       operating system's line separator.</li>
 * </ul>
 */
public class JsonHelper {
    private static final DefaultIndenter INDENTER = new DefaultIndenter("  ", "\n");
    private static final ObjectWriter PRETTY_WRITER = JsonMapping.mapper().rebuild()
            .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .build()
            .writer()
            .with(new DefaultPrettyPrinter()
                    .withObjectIndenter(INDENTER)
                    .withArrayIndenter(INDENTER));

    /**
     * Serialize a POJO or a JSON tree to pretty-printed JSON text.
     */
    public static String toPrettyString(Object value) {
        return PRETTY_WRITER.writeValueAsString(value);
    }

    public static void writePojo(Path file, Object pojo) throws IOException {
        Files.writeString(file, toPrettyString(pojo), StandardCharsets.UTF_8);
    }
}
