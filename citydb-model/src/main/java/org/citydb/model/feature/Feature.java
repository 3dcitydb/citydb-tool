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

package org.citydb.model.feature;

import org.citydb.model.common.*;
import org.citydb.model.geometry.Envelope;
import org.citydb.model.property.*;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Feature extends ModelObject<Feature> implements Describable<FeatureDescriptor> {
    private final Name featureType;
    private Envelope envelope;
    private OffsetDateTime lastModificationDate;
    private String updatingPerson;
    private String reasonForUpdate;
    private String lineage;
    private PropertyMap<Attribute> attributes;
    private PropertyMap<GeometryProperty> geometries;
    private PropertyMap<ImplicitGeometryProperty> implicitGeometries;
    private PropertyMap<FeatureProperty> features;
    private PropertyMap<AppearanceProperty> appearances;
    private PropertyMap<AddressProperty> addresses;
    private FeatureDescriptor descriptor;

    protected Feature(Name featureType) {
        this.featureType = Objects.requireNonNull(featureType, "The feature type must not be null.");
    }

    protected Feature(FeatureTypeProvider provider) {
        this(provider.getName());
    }

    public static Feature of(FeatureTypeProvider provider) {
        Objects.requireNonNull(provider, "The feature type provider must not be null.");
        return new Feature(provider);
    }

    public static Feature of(Name featureType) {
        return new Feature(featureType);
    }

    public Name getFeatureType() {
        return featureType;
    }

    public Optional<Envelope> getEnvelope() {
        return Optional.ofNullable(envelope);
    }

    public Feature setEnvelope(Envelope envelope) {
        this.envelope = asChild(envelope);
        return this;
    }

    public Optional<OffsetDateTime> getLastModificationDate() {
        return Optional.ofNullable(lastModificationDate);
    }

    public Feature setLastModificationDate(OffsetDateTime lastModificationDate) {
        this.lastModificationDate = lastModificationDate;
        return this;
    }

    public Optional<String> getUpdatingPerson() {
        return Optional.ofNullable(updatingPerson);
    }

    public Feature setUpdatingPerson(String updatingPerson) {
        this.updatingPerson = updatingPerson;
        return this;
    }

    public Optional<String> getReasonForUpdate() {
        return Optional.ofNullable(reasonForUpdate);
    }

    public Feature setReasonForUpdate(String reasonForUpdate) {
        this.reasonForUpdate = reasonForUpdate;
        return this;
    }

    public Optional<String> getLineage() {
        return Optional.ofNullable(lineage);
    }

    public Feature setLineage(String lineage) {
        this.lineage = lineage;
        return this;
    }

    public boolean hasAttributes() {
        return attributes != null && !attributes.isEmpty();
    }

    public PropertyMap<Attribute> getAttributes() {
        if (attributes == null) {
            attributes = new PropertyMap<>(this);
        }

        return attributes;
    }

    public Feature setAttributes(Collection<Attribute> attributes) {
        this.attributes = new PropertyMap<>(this, attributes);
        return this;
    }

    public Feature addAttribute(Attribute attribute) {
        if (attribute != null) {
            getAttributes().put(attribute);
        }

        return this;
    }

    public boolean hasGeometries() {
        return geometries != null && !geometries.isEmpty();
    }

    public PropertyMap<GeometryProperty> getGeometries() {
        if (geometries == null) {
            geometries = new PropertyMap<>(this);
        }

        return geometries;
    }

    public Feature setGeometries(Collection<GeometryProperty> geometries) {
        this.geometries = new PropertyMap<>(this, geometries);
        return this;
    }

    public Feature addGeometry(GeometryProperty geometry) {
        if (geometry != null) {
            getGeometries().put(geometry);
        }

        return this;
    }

    public boolean hasImplicitGeometries() {
        return implicitGeometries != null && !implicitGeometries.isEmpty();
    }

    public PropertyMap<ImplicitGeometryProperty> getImplicitGeometries() {
        if (implicitGeometries == null) {
            implicitGeometries = new PropertyMap<>(this);
        }

        return implicitGeometries;
    }

    public Feature setImplicitGeometries(Collection<ImplicitGeometryProperty> implicitGeometries) {
        this.implicitGeometries = new PropertyMap<>(this, implicitGeometries);
        return this;
    }

    public Feature addImplicitGeometry(ImplicitGeometryProperty implicitGeometry) {
        if (implicitGeometry != null) {
            getImplicitGeometries().put(implicitGeometry);
        }

        return this;
    }

    public boolean hasFeatures() {
        return features != null && !features.isEmpty();
    }

    public PropertyMap<FeatureProperty> getFeatures() {
        if (features == null) {
            features = new PropertyMap<>(this);
        }

        return features;
    }

    public Feature setFeatures(Collection<FeatureProperty> features) {
        this.features = new PropertyMap<>(this, features);
        return this;
    }

    public Feature addFeature(FeatureProperty feature) {
        if (feature != null) {
            getFeatures().put(feature);
        }

        return this;
    }

    public boolean hasAppearances() {
        return appearances != null && !appearances.isEmpty();
    }

    public PropertyMap<AppearanceProperty> getAppearances() {
        if (appearances == null) {
            appearances = new PropertyMap<>(this);
        }

        return appearances;
    }

    public Feature setAppearances(Collection<AppearanceProperty> appearances) {
        this.appearances = new PropertyMap<>(this, appearances);
        return this;
    }

    public Feature addAppearance(AppearanceProperty appearance) {
        if (appearance != null) {
            getAppearances().put(appearance);
        }

        return this;
    }

    public boolean hasAddresses() {
        return addresses != null && !addresses.isEmpty();
    }

    public PropertyMap<AddressProperty> getAddresses() {
        if (addresses == null) {
            addresses = new PropertyMap<>(this);
        }

        return addresses;
    }

    public Feature setAddresses(Collection<AddressProperty> addresses) {
        this.addresses = new PropertyMap<>(this, addresses);
        return this;
    }

    public Feature addAddress(AddressProperty address) {
        if (address != null) {
            getAddresses().put(address);
        }

        return this;
    }

    public boolean hasProperties() {
        return hasAttributes()
                || hasGeometries()
                || hasImplicitGeometries()
                || hasFeatures()
                || hasAppearances()
                || hasAddresses();
    }

    public List<Property<?>> getProperties() {
        return Stream.of(attributes, geometries, implicitGeometries, features, appearances, addresses)
                .filter(Objects::nonNull)
                .map(PropertyMap::getAll)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    public List<Property<?>> getPropertiesIf(Predicate<Property<?>> predicate) {
        return getProperties().stream()
                .filter(predicate)
                .collect(Collectors.toList());
    }

    public void addProperty(Property<?> property) {
        if (property instanceof FeatureProperty featureProperty) {
            addFeature(featureProperty);
        } else if (property instanceof GeometryProperty geometryProperty) {
            addGeometry(geometryProperty);
        } else if (property instanceof ImplicitGeometryProperty implicitGeometryProperty) {
            addImplicitGeometry(implicitGeometryProperty);
        } else if (property instanceof AppearanceProperty appearanceProperty) {
            addAppearance(appearanceProperty);
        } else if (property instanceof AddressProperty addressProperty) {
            addAddress(addressProperty);
        } else if (property instanceof Attribute attribute) {
            addAttribute(attribute);
        }
    }

    @Override
    public Optional<FeatureDescriptor> getDescriptor() {
        return Optional.ofNullable(descriptor);
    }

    @Override
    public Feature setDescriptor(FeatureDescriptor descriptor) {
        this.descriptor = descriptor;
        return this;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    @Override
    protected Feature self() {
        return this;
    }
}
