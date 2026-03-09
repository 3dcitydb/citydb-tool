/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.writer;

import org.citydb.core.file.OutputFile;
import org.citydb.io.writer.WriteException;
import org.citydb.io.writer.WriteOptions;
import org.citygml4j.cityjson.CityJSONContext;
import org.citygml4j.cityjson.model.CityJSONVersion;
import org.citygml4j.cityjson.writer.AbstractCityJSONWriter;
import org.citygml4j.cityjson.writer.CityJSONOutputFactory;
import org.citygml4j.cityjson.writer.OutputEncoding;

import java.util.Locale;
import java.util.Objects;

public class CityJSONWriterFactory {
    private final CityJSONContext context;
    private final WriteOptions options;
    private final CityJSONFormatOptions formatOptions;

    private CityJSONWriterFactory(CityJSONContext context, WriteOptions options, CityJSONFormatOptions formatOptions) {
        this.context = Objects.requireNonNull(context, "CityJSON context must not be null.");
        this.options = Objects.requireNonNull(options, "The write options must not be null.");
        this.formatOptions = Objects.requireNonNull(formatOptions, "The format options must not be null.");
    }

    public static CityJSONWriterFactory newInstance(CityJSONContext context, WriteOptions options, CityJSONFormatOptions formatOptions) {
        return new CityJSONWriterFactory(context, options, formatOptions);
    }

    public AbstractCityJSONWriter<?> createWriter(OutputFile file) throws WriteException {
        try {
            CityJSONOutputFactory factory = context.createCityJSONOutputFactory(formatOptions.getVersion())
                    .computeCityModelExtent(true)
                    .withVertexPrecision(formatOptions.getVertexPrecision())
                    .withTemplatePrecision(formatOptions.getTemplatePrecision())
                    .withTextureVertexPrecision(formatOptions.getTextureVertexPrecision())
                    .applyTransformation(formatOptions.isTransformCoordinates())
                    .transformTemplateGeometries(formatOptions.isReplaceTemplateGeometries())
                    .useMaterialDefaults(formatOptions.isUseMaterialDefaults())
                    .withFallbackTheme(formatOptions.getFallbackTheme())
                    .writeGenericAttributeTypes(formatOptions.isWriteGenericAttributeTypes());

            OutputEncoding encoding = getOutputEncoding(options.getEncoding().orElse(null));
            boolean shouldStream = formatOptions.isJsonLines() && formatOptions.getVersion() != CityJSONVersion.v1_0;

            AbstractCityJSONWriter<?> writer = shouldStream ?
                    factory.createCityJSONFeatureWriter(file.openStream(), encoding) :
                    factory.createCityJSONWriter(file.openStream(), encoding)
                            .withIndent(formatOptions.isPrettyPrint() ? "  " : null);

            return writer.setHtmlSafe(formatOptions.isHtmlSafe());
        } catch (Exception e) {
            throw new WriteException("Failed to create CityJSON writer.", e);
        }
    }

    private OutputEncoding getOutputEncoding(String encoding) {
        if (encoding != null) {
            return switch (encoding.toUpperCase(Locale.ROOT)) {
                case "UTF16", "UTF-16", "UTF-16BE" -> OutputEncoding.UTF16_BE;
                case "UTF-16LE" -> OutputEncoding.UTF16_LE;
                case "UTF32", "UTF-32", "UTF-32BE" -> OutputEncoding.UTF32_BE;
                case "UTF-32LE" -> OutputEncoding.UTF32_LE;
                default -> OutputEncoding.UTF8;
            };
        }

        return OutputEncoding.UTF8;
    }
}
