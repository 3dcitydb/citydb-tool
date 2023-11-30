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

package org.citydb.io.citygml;

import org.citydb.core.file.InputFile;
import org.citydb.io.FileFormat;
import org.citydb.io.IOAdapter;
import org.citydb.io.IOAdapterException;
import org.citydb.io.citygml.reader.CityGMLReader;
import org.citydb.io.citygml.writer.CityGMLWriter;
import org.citydb.io.reader.FeatureReader;
import org.citydb.io.validator.ValidateException;
import org.citydb.io.validator.Validator;
import org.citydb.io.writer.FeatureWriter;
import org.citygml4j.core.ade.ADERegistry;
import org.citygml4j.xml.CityGMLADELoader;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import org.xmlobjects.util.xml.SecureXMLProcessors;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;

@FileFormat(name = "CityGML",
        mediaType = "application/gml+xml",
        fileExtensions = {".gml", ".xml"})
public class CityGMLAdapter implements IOAdapter {
    private CityGMLAdapterContext context;

    @Override
    public void initialize(ClassLoader loader) throws IOAdapterException {
        context = new CityGMLAdapterContext(loader);
    }

    @Override
    public boolean canRead(InputFile file) {
        try (InputStream stream = file.openStream()) {
            XMLStreamReader reader = SecureXMLProcessors.newXMLInputFactory().createXMLStreamReader(stream);
            CityGMLADELoader loader = ADERegistry.getInstance().getADELoader(CityGMLADELoader.class);
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                    for (int i = 0; i < reader.getNamespaceCount(); i++) {
                        String namespaceURI = reader.getNamespaceURI(i);
                        if (CityGMLModules.isCityGMLNamespace(namespaceURI)
                                || loader.getADEModule(namespaceURI) != null) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            //
        }

        return false;
    }

    @Override
    public FeatureReader createReader() {
        return new CityGMLReader(context);
    }

    @Override
    public FeatureWriter createWriter() {
        return new CityGMLWriter(context);
    }

    @Override
    public Validator createValidator() throws ValidateException {
        return null;
    }
}
