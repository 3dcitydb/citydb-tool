/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
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

package org.citydb.io.citygml.writer;

import org.citydb.core.file.OutputFile;
import org.citydb.io.writer.WriteException;
import org.citydb.io.writer.WriteOptions;
import org.citygml4j.xml.CityGMLContext;
import org.citygml4j.xml.module.citygml.CoreModule;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class CityGMLWriterFactory {
    private final CityGMLContext context;
    private final WriteOptions options;
    private final CityGMLFormatOptions formatOptions;

    private CityGMLWriterFactory(CityGMLContext context, WriteOptions options, CityGMLFormatOptions formatOptions) {
        this.context = Objects.requireNonNull(context, "CityGML context must not be null.");
        this.options = Objects.requireNonNull(options, "The write options must not be null.");
        this.formatOptions = Objects.requireNonNull(formatOptions, "The format options must not be null.");
    }

    public static CityGMLWriterFactory newInstance(CityGMLContext context, WriteOptions options, CityGMLFormatOptions formatOptions) {
        return new CityGMLWriterFactory(context, options, formatOptions);
    }

    public CityGMLChunkWriter createWriter(OutputFile file) throws WriteException {
        try {
            String encoding = options.getEncoding().orElse(StandardCharsets.UTF_8.name());
            CityGMLChunkWriter writer = new CityGMLChunkWriter(file.openStream(), encoding, formatOptions.getVersion(), context)
                    .setDefaultPrefixes()
                    .setDefaultSchemaLocations()
                    .setDefaultNamespace(CoreModule.of(formatOptions.getVersion()).getNamespaceURI())
                    .setIndent(formatOptions.isPrettyPrint() ? "  " : null);

            writer.writeHeader();
            return writer;
        } catch (Exception e) {
            throw new WriteException("Failed to create CityGML writer.", e);
        }
    }
}
