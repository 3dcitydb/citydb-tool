/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.io.citygml.reader;

import org.citydb.core.file.InputFile;
import org.citydb.io.citygml.reader.options.LodMappingOptions;
import org.citydb.io.citygml.reader.util.CityJSONLodMapper;
import org.citydb.io.citygml.reader.util.IdCreator;
import org.citydb.io.reader.ReadException;
import org.citydb.io.reader.ReadOptions;
import org.citygml4j.cityjson.CityJSONContext;
import org.citygml4j.cityjson.reader.CityJSONInputFactory;
import org.citygml4j.cityjson.reader.CityJSONInputFilter;
import org.citygml4j.cityjson.reader.CityJSONReader;
import org.citygml4j.cityjson.util.lod.DefaultLodMapper;
import org.citygml4j.cityjson.util.lod.LodMapper;
import org.citygml4j.core.model.CityGMLVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class CityJSONReaderFactory {
    private final Logger logger = LoggerFactory.getLogger(CityJSONReaderFactory.class);
    private final CityJSONContext context;
    private final ReadOptions options;
    private final CityJSONFormatOptions formatOptions;
    private final String seed;
    private Map<Integer, Double> lodMappings;

    private CityJSONReaderFactory(CityJSONContext context, ReadOptions options, CityJSONFormatOptions formatOptions) {
        this.context = Objects.requireNonNull(context, "CityJSON context must not be null.");
        this.options = Objects.requireNonNull(options, "The read options must not be null.");
        this.formatOptions = Objects.requireNonNull(formatOptions, "The format options must not be null.");
        seed = "citydb-" + Long.toUnsignedString(ThreadLocalRandom.current().nextLong() ^ System.currentTimeMillis());
    }

    public static CityJSONReaderFactory newInstance(CityJSONContext context, ReadOptions options, CityJSONFormatOptions formatOptions) {
        return new CityJSONReaderFactory(context, options, formatOptions);
    }

    public CityJSONReader createReader(InputFile file) throws ReadException {
        return createReader(file, null);
    }

    public CityJSONReader createReader(InputFile file, CityJSONInputFilter filter) throws ReadException {
        try {
            CityJSONInputFactory inputFactory = context.createCityJSONInputFactory()
                    .chunkByTopLevelCityObjects(true)
                    .withTargetCityGMLVersion(CityGMLVersion.v3_0)
                    .assignAppearancesToImplicitGeometries(true)
                    .mapUnsupportedTypesToGenerics(formatOptions.isMapUnsupportedTypesToGenerics())
                    .withLodMapper(createLodMapper())
                    .withIdCreator(new IdCreator(seed));

            String encoding = options.getEncoding().orElse(null);
            CityJSONReader reader = encoding != null
                    ? inputFactory.createCityJSONReader(new BufferedReader(
                    new InputStreamReader(file.openStream(), encoding)))
                    : inputFactory.createCityJSONReader(file.openStream());
            if (filter != null) {
                reader = inputFactory.createFilteredCityJSONReader(reader, filter);
            }

            return reader;
        } catch (Exception e) {
            throw new ReadException("Failed to create CityJSON reader.", e);
        }
    }

    private LodMapper createLodMapper() {
        LodMappingOptions lodMappingOptions = formatOptions.getLodMapping().orElseGet(LodMappingOptions::new);
        LodMapper lodMapper = new DefaultLodMapper().withMappingStrategy(
                switch (lodMappingOptions.getStrategy()) {
                    case MAXIMUM -> DefaultLodMapper.Strategy.MAXIMUM_LOD;
                    case MINIMUM -> DefaultLodMapper.Strategy.MINIMUM_LOD;
                });

        if (lodMappings == null) {
            lodMappings = parseLodMappings(lodMappingOptions);
        }

        return !lodMappings.isEmpty()
                ? new CityJSONLodMapper(lodMappings, lodMapper)
                : lodMapper;
    }

    private Map<Integer, Double> parseLodMappings(LodMappingOptions lodMappingOptions) {
        Map<Integer, Double> mappings = new HashMap<>();
        if (lodMappingOptions.hasMappings()) {
            for (Map.Entry<Integer, String> entry : lodMappingOptions.getMappings().entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }

                double lod;
                try {
                    lod = Double.parseDouble(entry.getValue());
                } catch (NumberFormatException | NullPointerException e) {
                    logger.warn("Ignoring CityJSON LoD mapping for LoD {}: '{}' is not a valid CityJSON LoD.",
                            entry.getKey(), entry.getValue());
                    continue;
                }

                if ((int) lod != entry.getKey()) {
                    logger.warn("Ignoring CityJSON LoD mapping for LoD {}: the CityJSON LoD must be {}.x but is '{}'.",
                            entry.getKey(), entry.getKey(), entry.getValue());
                    continue;
                }

                mappings.put(entry.getKey(), lod);
            }
        }

        return mappings;
    }
}
