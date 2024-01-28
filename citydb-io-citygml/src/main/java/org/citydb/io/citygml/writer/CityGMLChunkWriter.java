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

package org.citydb.io.citygml.writer;

import org.citydb.io.writer.WriteException;
import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.core.*;
import org.citygml4j.xml.CityGMLContext;
import org.citygml4j.xml.module.Module;
import org.citygml4j.xml.module.ade.ADEModule;
import org.citygml4j.xml.module.citygml.AppearanceModule;
import org.citygml4j.xml.module.citygml.CityGMLModule;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import org.citygml4j.xml.module.citygml.CoreModule;
import org.citygml4j.xml.module.gml.GMLCoreModule;
import org.citygml4j.xml.transform.TransformerPipeline;
import org.citygml4j.xml.writer.SAXFragmentHandler;
import org.xml.sax.ContentHandler;
import org.xmlobjects.serializer.ObjectSerializeException;
import org.xmlobjects.stream.XMLWriteException;
import org.xmlobjects.stream.XMLWriter;
import org.xmlobjects.stream.XMLWriterFactory;
import org.xmlobjects.util.xml.SAXBuffer;
import org.xmlobjects.util.xml.SAXWriter;
import org.xmlobjects.xml.Element;
import org.xmlobjects.xml.Namespaces;

import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXResult;
import java.io.IOException;
import java.io.OutputStream;

public class CityGMLChunkWriter {
    private final SAXWriter writer;
    private final CityGMLVersion version;
    private final XMLWriterFactory factory;
    private final Namespaces namespaces;

    private TransformerPipeline transformer;
    private CityModel cityModel;
    private State state = State.INITIAL;

    private enum State {
        INITIAL,
        DOCUMENT_STARTED,
        CLOSED
    }

    CityGMLChunkWriter(OutputStream stream, String encoding, CityGMLVersion version, CityGMLContext context) throws IOException {
        this.writer = new SAXWriter(stream, encoding);
        this.version = version;
        factory = XMLWriterFactory.newInstance(context.getXMLObjects());

        namespaces = Namespaces.newInstance();
        for (Module module : CityGMLModules.of(version).getModules()) {
            namespaces.add(module.getNamespaceURI());
        }
    }

    String getPrefix(String namespaceURI) {
        return writer.getPrefix(namespaceURI);
    }

    CityGMLChunkWriter setPrefix(String prefix, String namespaceURI) {
        writer.withPrefix(prefix,namespaceURI);
        return this;
    }

    CityGMLChunkWriter setDefaultPrefixes() {
        for (Module module : CityGMLModules.of(version).getModules()) {
            setPrefix(module.getNamespacePrefix(), module.getNamespaceURI());
        }

        return this;
    }

    String getNamespaceURI(String prefix) {
        return writer.getNamespaceURI(prefix);
    }

    CityGMLChunkWriter setDefaultNamespace(String namespaceURI) {
        writer.withDefaultNamespace(namespaceURI);
        return this;
    }

    String getIndent() {
        return writer.getIndent();
    }

    CityGMLChunkWriter setIndent(String indent) {
        writer.withIndent(indent);
        return this;
    }

    boolean isWriteXMLDeclaration() {
        return writer.isWriteXMLDeclaration();
    }

    CityGMLChunkWriter writeXMLDeclaration(boolean writeXMLDeclaration) {
        writer.writeXMLDeclaration(writeXMLDeclaration);
        return this;
    }

    String[] getHeaderComment() {
        return writer.getHeaderComment();
    }

    CityGMLChunkWriter setHeaderComment(String... headerComment) {
        writer.withHeaderComment(headerComment);
        return this;
    }

    String getSchemaLocation(String namespaceURI) {
        return writer.getSchemaLocation(namespaceURI);
    }

    CityGMLChunkWriter setSchemaLocation(String namespaceURI, String schemaLocation) {
        writer.withSchemaLocation(namespaceURI, schemaLocation);
        return this;
    }

    CityGMLChunkWriter setDefaultSchemaLocations() {
        for (Module module : CityGMLModules.of(version).getModules()) {
            if (module.isSetSchemaLocation()
                    && ((module instanceof CityGMLModule && !(module instanceof CoreModule))
                    || module instanceof ADEModule)) {
                writer.withSchemaLocation(module.getNamespaceURI(), module.getSchemaLocation());
            }
        }

        return this;
    }

    TransformerPipeline getTransformer() {
        return transformer;
    }

    CityGMLChunkWriter setTransformer(TransformerPipeline transformer) {
        this.transformer = transformer;
        return this;
    }

    CityModel getCityModel() {
        if (cityModel == null) {
            cityModel = new CityModel();
        }

        return cityModel;
    }

    CityGMLChunkWriter setCityModel(CityModel cityModel) {
        this.cityModel = cityModel;
        return this;
    }

    ContentHandler getContentHandler() {
        return writer;
    }

    SAXBuffer bufferMember(AbstractFeature feature) throws WriteException {
        if (feature instanceof AbstractCityObject) {
            return bufferMember(feature, CoreModule.of(version).getNamespaceURI(), "cityObjectMember");
        } else if (feature instanceof AbstractAppearance) {
            return bufferMember(feature, version != CityGMLVersion.v3_0 ?
                    AppearanceModule.of(version).getNamespaceURI() :
                    CoreModule.of(version).getNamespaceURI(), "appearanceMember");
        } else if (version == CityGMLVersion.v3_0) {
            if (feature instanceof AbstractVersion) {
                return bufferMember(feature, CoreModule.v3_0.getNamespaceURI(), "versionMember");
            } else if (feature instanceof AbstractVersionTransition) {
                return bufferMember(feature, CoreModule.v3_0.getNamespaceURI(), "versionTransitionMember");
            } else {
                return bufferMember(feature, CoreModule.v3_0.getNamespaceURI(), "featureMember");
            }
        } else if (feature != null) {
            return bufferMember(feature, GMLCoreModule.v3_1.getNamespaceURI(), "featureMember");
        }

        return null;
    }

    private SAXBuffer bufferMember(AbstractFeature feature, String namespaceURI, String propertyName) throws WriteException {
        switch (state) {
            case CLOSED:
                throw new WriteException("Illegal to write features after writer has been closed.");
            case INITIAL:
                writeHeader();
        }

        try {
            SAXBuffer buffer = new SAXBuffer()
                    .useAsFragment(true)
                    .assumeMixedContent(false);
            XMLWriter writer = getWriter(buffer);
            writer.writeStartDocument();
            writer.writeStartElement(Element.of(namespaceURI, propertyName));
            writer.writeObject(feature, namespaces);
            writer.writeEndElement();
            writer.writeEndDocument();
            resetTransformer();
            return buffer;
        } catch (XMLWriteException | ObjectSerializeException | TransformerException e) {
            throw new WriteException("Caused by:", e);
        }
    }

    void writeHeader() throws WriteException {
        if (state != State.INITIAL) {
            throw new WriteException("The document has already been started.");
        }

        try {
            SAXFragmentHandler fragmentHandler = new SAXFragmentHandler(writer, SAXFragmentHandler.Mode.HEADER);
            XMLWriter writer = getWriter(fragmentHandler);
            writer.writeStartDocument();
            writer.writeObject(getCityModel(), namespaces);
            writer.writeEndDocument();
            resetTransformer();
        } catch (XMLWriteException | ObjectSerializeException | TransformerException e) {
            throw new WriteException("Caused by:", e);
        } finally {
            state = State.DOCUMENT_STARTED;
        }
    }

    private void writeFooter() throws WriteException {
        if (state == State.INITIAL) {
            writeHeader();
        }

        try {
            SAXFragmentHandler fragmentHandler = new SAXFragmentHandler(writer, SAXFragmentHandler.Mode.FOOTER);
            XMLWriter writer = getWriter(fragmentHandler);
            writer.writeStartDocument();
            writer.writeObject(new CityModel(), namespaces);
            writer.writeEndDocument();
        } catch (XMLWriteException | ObjectSerializeException e) {
            throw new WriteException("Caused by:", e);
        }
    }

    private XMLWriter getWriter(ContentHandler handler) {
        if (transformer == null)
            return factory.createWriter(handler);
        else {
            XMLWriter writer = factory.createWriter(transformer.getRootHandler());
            transformer.setResult(new SAXResult(handler));
            return writer;
        }
    }

    private void resetTransformer() throws TransformerException {
        if (transformer != null) {
            transformer.reset();
        }
    }

    void close() throws WriteException {
        if (state == State.CLOSED) {
            throw new WriteException("The writer has already been closed.");
        }

        try {
            writeFooter();
            writer.close();
        } catch (IOException e) {
            throw new WriteException("Caused by:", e);
        } finally {
            state = State.CLOSED;
        }
    }
}
