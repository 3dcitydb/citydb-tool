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

package org.citydb.model.property;

import org.citydb.model.common.Name;
import org.citydb.model.common.PropertyMap;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

public class Attribute extends Property<Attribute> {
    private Long intValue;
    private Double doubleValue;
    private String stringValue;
    private ArrayValue arrayValue;
    private OffsetDateTime timeStamp;
    private String uri;
    private String codeSpace;
    private String uom;
    private String genericContent;
    private String genericContentMimeType;
    private PropertyMap<Property<?>> properties;

    protected Attribute(Name name, Name dataType) {
        super(name, dataType);
    }

    protected Attribute(Name name, DataTypeProvider provider) {
        super(name, provider);
    }

    public static Attribute of(Name name, DataTypeProvider provider) {
        Objects.requireNonNull(provider, "The data type provider must not be null.");
        return new Attribute(name, provider);
    }

    public static Attribute of(Name name, Name dataType) {
        return new Attribute(name, dataType);
    }

    public static Attribute of(Name name) {
        return new Attribute(name, (Name) null);
    }

    public Optional<Long> getIntValue() {
        return Optional.ofNullable(intValue);
    }

    public Attribute setIntValue(Integer intValue) {
        this.intValue = intValue != null ? intValue.longValue() : null;
        return this;
    }

    public Attribute setIntValue(Long intValue) {
        this.intValue = intValue;
        return this;
    }

    public Optional<Double> getDoubleValue() {
        return Optional.ofNullable(doubleValue);
    }

    public Attribute setDoubleValue(Double doubleValue) {
        this.doubleValue = doubleValue;
        return this;
    }

    public Optional<String> getStringValue() {
        return Optional.ofNullable(stringValue);
    }

    public Attribute setStringValue(String stringValue) {
        this.stringValue = stringValue;
        return this;
    }

    public Optional<ArrayValue> getArrayValue() {
        return Optional.ofNullable(arrayValue);
    }

    public Attribute setArrayValue(ArrayValue arrayValue) {
        this.arrayValue = arrayValue;
        return this;
    }

    public Optional<OffsetDateTime> getTimeStamp() {
        return Optional.ofNullable(timeStamp);
    }

    public Attribute setTimeStamp(OffsetDateTime timeStamp) {
        this.timeStamp = timeStamp;
        return this;
    }

    public Optional<String> getURI() {
        return Optional.ofNullable(uri);
    }

    public Attribute setURI(String uri) {
        this.uri = uri;
        return this;
    }

    public Optional<String> getCodeSpace() {
        return Optional.ofNullable(codeSpace);
    }

    public Attribute setCodeSpace(String codeSpace) {
        this.codeSpace = codeSpace;
        return this;
    }

    public Optional<String> getUom() {
        return Optional.ofNullable(uom);
    }

    public Attribute setUom(String uom) {
        this.uom = uom;
        return this;
    }

    public Optional<String> getGenericContent() {
        return Optional.ofNullable(genericContent);
    }

    public Attribute setGenericContent(String genericContent) {
        this.genericContent = genericContent;
        return this;
    }

    public Optional<String> getGenericContentMimeType() {
        return Optional.ofNullable(genericContentMimeType);
    }

    public Attribute setGenericContentMimeType(String genericContentMimeType) {
        this.genericContentMimeType = genericContentMimeType;
        return this;
    }

    public Attribute setDataType(Name dataType) {
        return super.setDataType(dataType);
    }

    public Attribute setDataType(DataTypeProvider provider) {
        if (provider != null) {
            setDataType(provider.getName());
        }

        return this;
    }

    public Attribute setDataType(DataType dataType) {
        if (dataType != null) {
            setDataType(dataType.getName());
        }

        return this;
    }

    public boolean hasProperties() {
        return properties != null && !properties.isEmpty();
    }

    public PropertyMap<Property<?>> getProperties() {
        if (properties == null) {
            properties = new PropertyMap<>(this);
        }

        return properties;
    }

    public Attribute setProperties(Collection<Property<?>> properties) {
        this.properties = new PropertyMap<>(this, properties);
        return this;
    }

    public Attribute addProperty(Property<?> property) {
        if (property != null) {
            getProperties().put(property);
        }

        return this;
    }

    @Override
    Attribute self() {
        return this;
    }
}
