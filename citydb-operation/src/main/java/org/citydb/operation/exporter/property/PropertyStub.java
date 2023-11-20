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

package org.citydb.operation.exporter.property;

import org.citydb.model.common.Name;
import org.citydb.model.common.ReferenceType;
import org.citydb.model.geometry.Point;
import org.citydb.model.property.ArrayValue;
import org.citydb.model.property.DataType;
import org.citydb.model.property.PropertyDescriptor;

import java.time.OffsetDateTime;

public class PropertyStub {
    private final Name name;
    private DataType dataType;
    private Long intValue;
    private Double doubleValue;
    private String stringValue;
    private OffsetDateTime timeStamp;
    private String uri;
    private String codeSpace;
    private String uom;
    private ArrayValue arrayValue;
    private String lod;
    private Long geometryId;
    private Long implicitGeometryId;
    private Point referencePoint;
    private Long appearanceId;
    private Long addressId;
    private Long featureId;
    private ReferenceType referenceType;
    private String genericContent;
    private String genericContentMimeType;
    private PropertyDescriptor descriptor;

    PropertyStub(Name name) {
        this.name = name;
    }

    static PropertyStub of(Name name) {
        return new PropertyStub(name);
    }

    public Name getName() {
        return name;
    }

    public DataType getDataType() {
        return dataType;
    }

    public PropertyStub setDataType(DataType dataType) {
        this.dataType = dataType;
        return this;
    }

    public Long getIntValue() {
        return intValue;
    }

    public PropertyStub setIntValue(Long intValue) {
        this.intValue = intValue;
        return this;
    }

    public Double getDoubleValue() {
        return doubleValue;
    }

    public PropertyStub setDoubleValue(Double doubleValue) {
        this.doubleValue = doubleValue;
        return this;
    }

    public String getStringValue() {
        return stringValue;
    }

    public PropertyStub setStringValue(String stringValue) {
        this.stringValue = stringValue;
        return this;
    }

    public OffsetDateTime getTimeStamp() {
        return timeStamp;
    }

    public PropertyStub setTimeStamp(OffsetDateTime timeStamp) {
        this.timeStamp = timeStamp;
        return this;
    }

    public String getURI() {
        return uri;
    }

    public PropertyStub setURI(String uri) {
        this.uri = uri;
        return this;
    }

    public String getCodeSpace() {
        return codeSpace;
    }

    public PropertyStub setCodeSpace(String codeSpace) {
        this.codeSpace = codeSpace;
        return this;
    }

    public String getUom() {
        return uom;
    }

    public PropertyStub setUom(String uom) {
        this.uom = uom;
        return this;
    }

    public ArrayValue getArrayValue() {
        return arrayValue;
    }

    public PropertyStub setArrayValue(ArrayValue arrayValue) {
        this.arrayValue = arrayValue;
        return this;
    }

    public String getLod() {
        return lod;
    }

    public PropertyStub setLod(String lod) {
        this.lod = lod;
        return this;
    }

    public Long getGeometryId() {
        return geometryId;
    }

    public PropertyStub setGeometryId(Long geometryId) {
        this.geometryId = geometryId;
        return this;
    }

    public Long getImplicitGeometryId() {
        return implicitGeometryId;
    }

    public PropertyStub setImplicitGeometryId(Long implicitGeometryId) {
        this.implicitGeometryId = implicitGeometryId;
        return this;
    }

    public Point getReferencePoint() {
        return referencePoint;
    }

    public PropertyStub setReferencePoint(Point referencePoint) {
        this.referencePoint = referencePoint;
        return this;
    }

    public Long getAppearanceId() {
        return appearanceId;
    }

    public PropertyStub setAppearanceId(Long appearanceId) {
        this.appearanceId = appearanceId;
        return this;
    }

    public Long getAddressId() {
        return addressId;
    }

    public PropertyStub setAddressId(Long addressId) {
        this.addressId = addressId;
        return this;
    }

    public Long getFeatureId() {
        return featureId;
    }

    public PropertyStub setFeatureId(Long featureId) {
        this.featureId = featureId;
        return this;
    }

    public ReferenceType getReferenceType() {
        return referenceType;
    }

    public PropertyStub setReferenceType(ReferenceType referenceType) {
        this.referenceType = referenceType;
        return this;
    }

    public String getGenericContent() {
        return genericContent;
    }

    public PropertyStub setGenericContent(String genericContent) {
        this.genericContent = genericContent;
        return this;
    }

    public String getGenericContentMimeType() {
        return genericContentMimeType;
    }

    public PropertyStub setGenericContentMimeType(String genericContentMimeType) {
        this.genericContentMimeType = genericContentMimeType;
        return this;
    }

    public PropertyDescriptor getDescriptor() {
        return descriptor;
    }

    public PropertyStub setDescriptor(PropertyDescriptor descriptor) {
        this.descriptor = descriptor;
        return this;
    }
}
