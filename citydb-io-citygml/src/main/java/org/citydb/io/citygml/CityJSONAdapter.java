/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2023
 * Virtual City Systems, Germany
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

package org.citydb.io.citygml;

import org.citydb.core.file.InputFile;
import org.citydb.io.FileFormat;
import org.citydb.io.IOAdapter;
import org.citydb.io.IOAdapterException;
import org.citydb.io.citygml.reader.CityJSONReader;
import org.citydb.io.citygml.writer.CityJSONWriter;
import org.citydb.io.reader.FeatureReader;
import org.citydb.io.validator.ValidateException;
import org.citydb.io.validator.Validator;
import org.citydb.io.writer.FeatureWriter;
import org.citygml4j.cityjson.CityJSONContext;
import org.citygml4j.cityjson.CityJSONContextException;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

@FileFormat(name = "CityJSON",
        mediaType = "application/city+json",
        fileExtensions = {".json", ".jsonl"})
public class CityJSONAdapter implements IOAdapter {
    private CityGMLAdapterContext adapterContext;
    private CityJSONContext cityJSONContext;

    @Override
    public void initialize(ClassLoader loader) throws IOAdapterException {
        adapterContext = new CityGMLAdapterContext(loader);
        try {
            cityJSONContext = CityJSONContext.newInstance(loader);
        } catch (CityJSONContextException e) {
            throw new IOAdapterException("Failed to create CityJSON context.", e);
        }
    }

    @Override
    public boolean canRead(InputFile file) {
        try (InputStream stream = file.openStream()) {
            byte[] buffer = new byte[1024];
            if (stream.read(buffer) > 0) {
                return Stream.of(new String(buffer, StandardCharsets.UTF_8),
                                new String(buffer, StandardCharsets.UTF_16BE),
                                new String(buffer, StandardCharsets.UTF_16LE),
                                new String(buffer, Charset.forName("UTF-32BE")),
                                new String(buffer, Charset.forName("UTF-32LE")),
                                new String(buffer))
                        .anyMatch(v -> v.contains("CityJSON"));
            }
        } catch (Exception e) {
            //
        }

        return false;
    }

    @Override
    public FeatureReader createReader() {
        return new CityJSONReader(adapterContext, cityJSONContext);
    }

    @Override
    public FeatureWriter createWriter() {
        return new CityJSONWriter(adapterContext, cityJSONContext);
    }

    @Override
    public Validator createValidator() throws ValidateException {
        return null;
    }
}
