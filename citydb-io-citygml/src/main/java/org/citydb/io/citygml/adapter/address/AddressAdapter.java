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

package org.citydb.io.citygml.adapter.address;

import org.apache.tika.mime.MimeTypes;
import org.citydb.io.citygml.builder.ModelBuildException;
import org.citydb.io.citygml.builder.ModelBuilder;
import org.citydb.io.citygml.reader.ModelBuilderHelper;
import org.citydb.io.citygml.serializer.ModelSerializeException;
import org.citydb.io.citygml.serializer.ModelSerializer;
import org.citydb.io.citygml.writer.ModelSerializerHelper;
import org.citydb.io.citygml.writer.options.AddressMode;
import org.citydb.model.address.Address;
import org.citydb.model.geometry.MultiPoint;
import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.core.XALAddressProperty;
import org.citygml4j.xml.module.Module;
import org.citygml4j.xml.module.xal.XALCommonTypesModule;
import org.citygml4j.xml.module.xal.XALCoreModule;
import org.xmlobjects.gml.model.basictypes.CodeWithAuthority;
import org.xmlobjects.gml.model.geometry.aggregates.MultiPointProperty;

public class AddressAdapter implements ModelBuilder<org.citygml4j.core.model.core.Address, Address>, ModelSerializer<Address, org.citygml4j.core.model.core.Address> {
    private final AddressBuilder builder = new AddressBuilder();
    private final AddressSerializer serializer = new AddressSerializer();

    @Override
    public void build(org.citygml4j.core.model.core.Address source, Address target, ModelBuilderHelper helper) throws ModelBuildException {
        target.setObjectId(source.getId());

        if (source.getIdentifier() != null && source.getIdentifier().getValue() != null) {
            target.setIdentifier(source.getIdentifier().getValue())
                    .setIdentifierCodeSpace(source.getIdentifier().getCodeSpace());
        }

        if (source.getXALAddress() != null && source.getXALAddress().getObject() != null) {
            builder.build(source.getXALAddress().getObject(), target);
            if (helper.isImportXALSource()) {
                target.setGenericContentMimeType(MimeTypes.XML)
                        .setGenericContent(helper.toXML(source.getXALAddress().getObject(),
                                helper.getCityGMLVersion() == CityGMLVersion.v3_0 ?
                                        new Module[]{XALCoreModule.v3_0, XALCommonTypesModule.v3_0} :
                                        new Module[]{XALCoreModule.v2_0}));
            }
        }

        if (source.getMultiPoint() != null && source.getMultiPoint().getObject() != null) {
            target.setMultiPoint(helper.getPointGeometry(source.getMultiPoint().getObject(), MultiPoint.class));
        }
    }

    @Override
    public org.citygml4j.core.model.core.Address createObject(Address source) throws ModelSerializeException {
        return new org.citygml4j.core.model.core.Address();
    }

    @Override
    public void serialize(Address source, org.citygml4j.core.model.core.Address target, ModelSerializerHelper helper) throws ModelSerializeException {
        source.getObjectId().ifPresent(target::setId);

        source.getIdentifier().ifPresent(identifier -> target.setIdentifier(
                new CodeWithAuthority(identifier, source.getIdentifierCodeSpace().orElse(null))));

        org.xmlobjects.xal.model.Address address = null;
        if (helper.getAddressMode() == AddressMode.COLUMNS_FIRST
                || helper.getAddressMode() == AddressMode.COLUMNS_ONLY) {
            address = serializer.serialize(source);
        } else if (helper.getAddressMode() == AddressMode.XAL_SOURCE_FIRST
                || helper.getAddressMode() == AddressMode.XAL_SOURCE_ONLY) {
            address = helper.fromXML(source.getGenericContent().orElse(null), org.xmlobjects.xal.model.Address.class);
        }

        if (address == null) {
            if (helper.getAddressMode() == AddressMode.COLUMNS_FIRST) {
                address = helper.fromXML(source.getGenericContent().orElse(null), org.xmlobjects.xal.model.Address.class);
            } else if (helper.getAddressMode() == AddressMode.XAL_SOURCE_FIRST) {
                address = serializer.serialize(source);
            }
        }

        target.setXALAddress(new XALAddressProperty(address != null ?
                address :
                new org.xmlobjects.xal.model.Address()));

        source.getMultiPoint().ifPresent(multiPoint ->
                target.setMultiPoint(new MultiPointProperty(helper.getMultiPoint(multiPoint))));
    }
}
