/*
 * citydb-tool - Command-line tool for the 3D City Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2022-2025
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

import org.citydb.model.common.Child;
import org.citydb.model.common.InlineOrByReferenceProperty;
import org.citydb.model.common.Name;
import org.citydb.model.feature.Feature;

import java.util.Objects;
import java.util.Optional;

public class FeatureProperty extends Property<FeatureProperty> implements InlineOrByReferenceProperty<Feature> {
    private Feature feature;
    private String reference;
    private RelationType relationType;

    private FeatureProperty(Name name, Feature feature, RelationType relationType) {
        super(name, DataType.FEATURE_PROPERTY);
        this.feature = asChild(Objects.requireNonNull(feature, "The feature must not be null."));
        this.relationType = Objects.requireNonNull(relationType, "The relation type must not be null.");
    }

    private FeatureProperty(Name name, String reference, RelationType relationType) {
        super(name, DataType.FEATURE_PROPERTY);
        this.reference = Objects.requireNonNull(reference, "The reference must not be null.");
        this.relationType = Objects.requireNonNull(relationType, "The relation type must not be null.");
    }

    public static FeatureProperty of(Name name, Feature feature, RelationType relationType) {
        return new FeatureProperty(name, feature, relationType);
    }

    public static FeatureProperty of(Name name, String reference, RelationType relationType) {
        return new FeatureProperty(name, reference, relationType);
    }

    public static FeatureProperty asReference(Name name, Feature feature, RelationType relationType) {
        Objects.requireNonNull(feature, "The referenced feature must not be null.");
        return new FeatureProperty(name, feature.getOrCreateObjectId(), relationType);
    }

    @Override
    public Optional<Feature> getObject() {
        return Optional.ofNullable(feature);
    }

    @Override
    public FeatureProperty setObject(Feature feature) {
        if (feature != null) {
            this.feature = asChild(feature);
            reference = null;
        }

        return this;
    }

    @Override
    public Optional<String> getReference() {
        return Optional.ofNullable(reference);
    }

    @Override
    public FeatureProperty setReference(String reference) {
        if (reference != null) {
            this.reference = reference;
            feature = null;
        }

        return this;
    }

    @Override
    public FeatureProperty setReference(Feature feature) {
        return feature != null ? setReference(feature.getOrCreateObjectId()) : this;
    }

    public RelationType getRelationType() {
        return relationType != null ? relationType : RelationType.RELATES;
    }

    public FeatureProperty setRelationType(RelationType relationType) {
        this.relationType = relationType;
        return this;
    }

    @Override
    public boolean removeFromParent() {
        Child parent = getParent().orElse(null);
        if (parent instanceof Feature feature) {
            return feature.getFeatures().remove(this);
        } else if (parent instanceof Attribute attribute) {
            return attribute.getProperties().remove(this);
        } else {
            return false;
        }
    }

    @Override
    FeatureProperty self() {
        return this;
    }
}
