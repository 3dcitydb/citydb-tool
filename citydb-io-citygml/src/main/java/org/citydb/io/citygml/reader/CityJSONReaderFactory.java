/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2024
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

package org.citydb.io.citygml.reader;

import org.citydb.core.file.InputFile;
import org.citydb.io.citygml.reader.util.IdCreator;
import org.citydb.io.reader.ReadException;
import org.citydb.io.reader.ReadOptions;
import org.citygml4j.cityjson.CityJSONContext;
import org.citygml4j.cityjson.reader.CityJSONInputFactory;
import org.citygml4j.cityjson.reader.CityJSONInputFilter;
import org.citygml4j.cityjson.reader.CityJSONReader;
import org.citygml4j.core.model.CityGMLVersion;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.SecureRandom;
import java.util.Objects;

public class CityJSONReaderFactory {
    private final CityJSONContext context;
    private final ReadOptions options;
    private final CityJSONFormatOptions formatOptions;
    private final String seed;

    private CityJSONReaderFactory(CityJSONContext context, ReadOptions options, CityJSONFormatOptions formatOptions) {
        this.context = Objects.requireNonNull(context, "CityJSON context must not be null.");
        this.options = Objects.requireNonNull(options, "The read options must not be null.");
        this.formatOptions = Objects.requireNonNull(formatOptions, "The format options must not be null.");
        seed = "citydb-" + Long.toUnsignedString(new SecureRandom().nextLong() ^ System.currentTimeMillis());
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
                    .withIdCreator(new IdCreator(seed));

            String encoding = options.getEncoding().orElse(null);
            CityJSONReader reader = encoding != null ?
                    inputFactory.createCityJSONReader(new BufferedReader(
                            new InputStreamReader(file.openStream(), encoding))) :
                    inputFactory.createCityJSONReader(file.openStream());
            if (filter != null) {
                reader = inputFactory.createFilteredCityJSONReader(reader, filter);
            }

            return reader;
        } catch (Exception e) {
            throw new ReadException("Failed to create CityJSON reader.", e);
        }
    }
}
