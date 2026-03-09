/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.writer;

import org.citydb.core.file.OutputFile;
import org.citydb.io.writer.WriteException;
import org.citydb.io.writer.WriteOptions;
import org.citygml4j.xml.CityGMLContext;
import org.citygml4j.xml.module.citygml.CoreModule;
import org.citygml4j.xml.transform.TransformerPipeline;

import javax.xml.transform.stream.StreamSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
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

            if (formatOptions.hasXslTransforms()) {
                writer.setTransformer(getTransformer(formatOptions.getXslTransforms()));
            }

            writer.writeHeader();
            return writer;
        } catch (Exception e) {
            throw new WriteException("Failed to create CityGML writer.", e);
        }
    }

    private TransformerPipeline getTransformer(List<String> stylesheets) throws WriteException {
        try {
            return TransformerPipeline.newInstance(stylesheets.stream()
                    .map(Path::of)
                    .map(Path::toFile)
                    .map(StreamSource::new)
                    .toArray(StreamSource[]::new));
        } catch (Exception e) {
            throw new WriteException("Failed to build XSL transformation pipeline.", e);
        }
    }
}
