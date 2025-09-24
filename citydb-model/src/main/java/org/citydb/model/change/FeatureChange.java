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

package org.citydb.model.change;

import org.citydb.model.common.Child;
import org.citydb.model.common.Describable;
import org.citydb.model.common.Identifiable;
import org.citydb.model.common.Name;
import org.citydb.model.feature.FeatureTypeProvider;
import org.citydb.model.geometry.Envelope;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

public class FeatureChange extends Child implements Identifiable, Describable<FeatureChangeDescriptor> {
    private final Name featureType;
    private String objectId;
    private String identifier;
    private String identifierCodeSpace;
    private Envelope envelope;
    private TransactionType transactionType;
    private OffsetDateTime transactionDate;
    private String databaseUser;
    private String reasonForUpdate;
    private FeatureChangeDescriptor descriptor;

    private FeatureChange(Name featureType) {
        this.featureType = Objects.requireNonNull(featureType, "The feature type must not be null.");
    }

    private FeatureChange(FeatureTypeProvider provider) {
        this(provider.getName());
    }

    public static FeatureChange of(FeatureTypeProvider provider) {
        Objects.requireNonNull(provider, "The feature type provider must not be null.");
        return new FeatureChange(provider);
    }

    public static FeatureChange of(Name featureType) {
        return new FeatureChange(featureType);
    }

    public Name getFeatureType() {
        return featureType;
    }

    @Override
    public Optional<String> getObjectId() {
        return Optional.ofNullable(objectId);
    }

    @Override
    public FeatureChange setObjectId(String objectId) {
        this.objectId = objectId;
        return this;
    }

    @Override
    public Optional<String> getIdentifier() {
        return Optional.ofNullable(identifier);
    }

    @Override
    public FeatureChange setIdentifier(String identifier) {
        this.identifier = identifier;
        return this;
    }

    @Override
    public Optional<String> getIdentifierCodeSpace() {
        return Optional.ofNullable(identifierCodeSpace);
    }

    @Override
    public FeatureChange setIdentifierCodeSpace(String identifierCodeSpace) {
        this.identifierCodeSpace = identifierCodeSpace;
        return this;
    }

    public Optional<Envelope> getEnvelope() {
        return Optional.ofNullable(envelope);
    }

    public FeatureChange setEnvelope(Envelope envelope) {
        this.envelope = asChild(envelope);
        return this;
    }

    public Optional<TransactionType> getTransactionType() {
        return Optional.ofNullable(transactionType);
    }

    public FeatureChange setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
        return this;
    }

    public Optional<OffsetDateTime> getTransactionDate() {
        return Optional.ofNullable(transactionDate);
    }

    public FeatureChange setTransactionDate(OffsetDateTime transactionDate) {
        this.transactionDate = transactionDate;
        return this;
    }

    public Optional<String> getDatabaseUser() {
        return Optional.ofNullable(databaseUser);
    }

    public FeatureChange setDatabaseUser(String databaseUser) {
        this.databaseUser = databaseUser;
        return this;
    }

    public Optional<String> getReasonForUpdate() {
        return Optional.ofNullable(reasonForUpdate);
    }

    public FeatureChange setReasonForUpdate(String reasonForUpdate) {
        this.reasonForUpdate = reasonForUpdate;
        return this;
    }

    @Override
    public Optional<FeatureChangeDescriptor> getDescriptor() {
        return Optional.ofNullable(descriptor);
    }

    @Override
    public FeatureChange setDescriptor(FeatureChangeDescriptor descriptor) {
        this.descriptor = descriptor;
        return this;
    }
}
