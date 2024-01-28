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
import org.citygml4j.xml.CityGMLContext;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import org.citygml4j.xml.module.citygml.CoreModule;
import org.citygml4j.xml.reader.CityGMLReader;
import org.citygml4j.xml.reader.*;
import org.xmlobjects.xml.TextContent;

import java.security.SecureRandom;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class CityGMLReaderFactory {
    private final CityGMLContext context;
    private final String seed;
    private ReadOptions options;

    private CityGMLReaderFactory(CityGMLContext context) {
        this.context = Objects.requireNonNull(context, "CityGML context must not be null.");
        seed = "citydb-" + Long.toUnsignedString(new SecureRandom().nextLong() ^ System.currentTimeMillis());
        TextContent.setZoneOffsetProvider(localDateTime -> ZoneOffset.UTC);
    }

    public static CityGMLReaderFactory newInstance(CityGMLContext context) {
        return new CityGMLReaderFactory(context);
    }

    public CityGMLReaderFactory setReadOptions(ReadOptions options) {
        this.options = options;
        return this;
    }

    public CityGMLInputFactory createInputFactory() throws ReadException {
        try {
            return context.createCityGMLInputFactory()
                    .withChunking(ChunkOptions.defaults()
                            .withProperty(CoreModule.v2_0.getNamespaceURI(), "generalizesTo")
                            .withProperty(CoreModule.v1_0.getNamespaceURI(), "generalizesTo"))
                    .failOnMissingADESchema(false)
                    .withIdCreator(new IdCreator(seed));
        } catch (CityGMLReadException e) {
            throw new ReadException("Failed to create CityGML input factory.", e);
        }
    }

    public CityGMLReader createReader(InputFile file, CityGMLInputFactory inputFactory) throws ReadException {
        return createReader(file, inputFactory, (CityGMLInputFilter) null);
    }

    public CityGMLReader createReader(InputFile file, CityGMLInputFactory inputFactory, String... localNames) throws ReadException {
        CityGMLInputFilter filter = null;
        if (localNames != null) {
            Set<String> names = new HashSet<>(Arrays.asList(localNames));
            filter = name -> !names.contains(name.getLocalPart())
                    || !CityGMLModules.isCityGMLNamespace(name.getNamespaceURI());
        }

        return createReader(file, inputFactory, filter);
    }

    public CityGMLReader createReader(InputFile file, CityGMLInputFactory inputFactory, CityGMLInputFilter filter) throws ReadException {
        try {
            CityGMLReader reader = inputFactory.createCityGMLReader(file.openStream(),
                    options.getEncoding().orElse(null));
            if (filter != null) {
                reader = inputFactory.createFilteredCityGMLReader(reader, filter);
            }

            return reader;
        } catch (Exception e) {
            throw new ReadException("Failed to create CityGML reader.", e);
        }
    }
}
