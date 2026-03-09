/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright virtualcitysystems GmbH <https://vc.systems>
 */

package org.citydb.operation.exporter.hierarchy;

import org.citydb.model.address.Address;
import org.citydb.model.appearance.Appearance;
import org.citydb.model.feature.Feature;
import org.citydb.model.geometry.Geometry;
import org.citydb.model.geometry.ImplicitGeometry;
import org.citydb.model.property.*;
import org.citydb.operation.exporter.ExportHelper;
import org.citydb.operation.exporter.property.PropertyStub;

public class PropertyBuilder {
    private final ExportHelper helper;

    PropertyBuilder(ExportHelper helper) {
        this.helper = helper;
    }

    Property<?> build(PropertyStub propertyStub, Hierarchy hierarchy) {
        if (propertyStub != null && propertyStub.getDataType() != null) {
            Property<?> property = switch (propertyStub.getDataType()) {
                case FEATURE_PROPERTY -> buildFeatureProperty(propertyStub, hierarchy);
                case GEOMETRY_PROPERTY -> buildGeometryProperty(propertyStub, hierarchy);
                case IMPLICIT_GEOMETRY_PROPERTY -> buildImplicitGeometryProperty(propertyStub, hierarchy);
                case APPEARANCE_PROPERTY -> buildAppearanceProperty(propertyStub, hierarchy);
                case ADDRESS_PROPERTY -> buildAddressProperty(propertyStub, hierarchy);
                default -> buildAttribute(propertyStub);
            };

            if (property != null) {
                return property.setDescriptor(propertyStub.getDescriptor());
            }
        }

        return null;
    }

    <T extends Property<?>> T build(PropertyStub propertyStub, Hierarchy hierarchy, Class<T> type) {
        Property<?> property = build(propertyStub, hierarchy);
        return type.isInstance(property) ? type.cast(property) : null;
    }

    private FeatureProperty buildFeatureProperty(PropertyStub propertyStub, Hierarchy hierarchy) {
        Feature feature = hierarchy.getFeature(propertyStub.getFeatureId());
        if (feature != null) {
            return propertyStub.getRelationType() == RelationType.RELATES || helper.lookupAndPut(feature) ?
                    FeatureProperty.of(propertyStub.getName(), feature.getOrCreateObjectId(),
                            propertyStub.getRelationType()) :
                    FeatureProperty.of(propertyStub.getName(), feature, propertyStub.getRelationType());
        }

        return null;
    }

    private GeometryProperty buildGeometryProperty(PropertyStub propertyStub, Hierarchy hierarchy) {
        Geometry<?> geometry = hierarchy.getGeometry(propertyStub.getGeometryId());
        return geometry != null ?
                GeometryProperty.of(propertyStub.getName(), geometry)
                        .setLod(propertyStub.getLod()) :
                null;
    }

    private ImplicitGeometryProperty buildImplicitGeometryProperty(PropertyStub propertyStub, Hierarchy hierarchy) {
        ImplicitGeometry implicitGeometry = hierarchy.getImplicitGeometry(propertyStub.getImplicitGeometryId());
        if (implicitGeometry != null) {
            ImplicitGeometryProperty property = helper.lookupAndPut(implicitGeometry) ?
                    ImplicitGeometryProperty.of(propertyStub.getName(), implicitGeometry.getOrCreateObjectId()) :
                    ImplicitGeometryProperty.of(propertyStub.getName(), implicitGeometry);

            if (propertyStub.getArrayValue() != null) {
                property.setTransformationMatrix(propertyStub.getArrayValue().getValues().stream()
                        .filter(Value::isDouble)
                        .map(Value::doubleValue)
                        .toList());
            }

            return property.setReferencePoint(propertyStub.getReferencePoint())
                    .setLod(propertyStub.getLod());
        }

        return null;
    }

    private AppearanceProperty buildAppearanceProperty(PropertyStub propertyStub, Hierarchy hierarchy) {
        Appearance appearance = hierarchy.getAppearance(propertyStub.getAppearanceId());
        return appearance != null ?
                AppearanceProperty.of(propertyStub.getName(), appearance) :
                null;
    }

    private AddressProperty buildAddressProperty(PropertyStub propertyStub, Hierarchy hierarchy) {
        Address address = hierarchy.getAddress(propertyStub.getAddressId());
        if (address != null) {
            return helper.lookupAndPut(address) ?
                    AddressProperty.of(propertyStub.getName(), address.getOrCreateObjectId()) :
                    AddressProperty.of(propertyStub.getName(), address);
        }

        return null;
    }

    private Attribute buildAttribute(PropertyStub propertyStub) {
        return Attribute.of(propertyStub.getName(), propertyStub.getDataType())
                .setIntValue(propertyStub.getIntValue())
                .setDoubleValue(propertyStub.getDoubleValue())
                .setStringValue(propertyStub.getStringValue())
                .setArrayValue(propertyStub.getArrayValue())
                .setTimeStamp(propertyStub.getTimeStamp())
                .setURI(propertyStub.getURI())
                .setCodeSpace(propertyStub.getCodeSpace())
                .setUom(propertyStub.getUom())
                .setGenericContent(propertyStub.getGenericContent())
                .setGenericContentMimeType(propertyStub.getGenericContentMimeType());
    }
}
